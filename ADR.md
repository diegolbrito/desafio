# ADR.md - $$$$$$$$ Credit Engine

> Architecture Decision Record. Documento único cobrindo a arquitetura da solução como um todo,
> diferente do [`DECISIONS.md`](DECISIONS.md) (registro de decisões pontuais, uma por vez, com seu
> próprio contexto) e do [`SPEC.md`](SPEC.md) (premissas de negócio e convenções técnicas
> detalhadas).
>
> **Status**: Aceito. **Última atualização**: 2026-09-20.

## Contexto e Problema

A $$$$$$$$ é referência em fundos de investimento, especialmente FIDCs (Fundos de Investimento em
Direitos Creditórios). A operação consiste em adquirir ativos (duplicatas, contratos, recebíveis)
de empresas cedentes, provendo liquidez ao mercado. Com a globalização do portfólio, o fundo passou
a operar com caixa multimoedas (BRL e USD).

A mesa de operações precisa de um sistema, o **$$$$$$$$ Credit Engine**, responsável por precificar e
liquidar esses ativos com segurança e precisão decimal. O problema concreto a resolver: **receber
um lote de recebíveis, calcular o deságio (desconto) com base no risco do ativo e na moeda de
pagamento, e registrar a transação de forma auditável.**

## Objetivo

- Receber um lote de recebíveis via API e precificá-lo automaticamente (sem etapa de aprovação),
  calculando o deságio de cada item pelo método de desconto composto por valor presente, com base
  na taxa base da moeda, no spread de risco da categoria do recebível e num custo operacional fixo.
- Suportar conversão cross-currency (ativo sempre em BRL, pagamento em BRL ou USD), convertendo
  apenas o valor presente para a moeda de pagamento, com a cotação obtida de um serviço externo e
  resiliência caso esse serviço esteja indisponível.
- Registrar cada fato relevante do ciclo de vida do lote/recebível de forma auditável e imutável
  (eventos append-only), permitindo reconstituir o histórico mesmo que parâmetros de referência
  (taxas, câmbio) mudem depois.
- Permitir a liquidação (pagamento) de um recebível já precificado, com garantia de idempotência,
  a mesma requisição repetida não pode gerar duas liquidações.
- Expor a API de forma documentada (OpenAPI/Swagger) e observável (métricas de negócio e
  infraestrutura, dashboards).
- Demonstrar domínio da regra de negócio e da arquitetura do serviço, este é explicitamente, o
  objetivo do desafio em si (ver `DECISIONS.md`, item 1): a camada de identidade/segurança, num
  cenário real, seria delegada a um provedor corporativo existente, não reimplementada aqui.

## Direcionamento de arquitetura

### Arquitetura hexagonal (ports & adapters), rigorosa

O backend é organizado em camadas com direção de dependência explícita e verificável:
**domínio** (entidades, regras de negócio, exceções, sem nenhuma dependência de framework, banco,
HTTP ou biblioteca de infraestrutura) → **aplicação** (casos de uso que orquestram o domínio,
expondo *ports* de entrada e dependendo só de *ports* de saída, como interfaces) → **adapters**
(implementações concretas: REST na entrada; persistência, cliente HTTP externo na saída). A
injeção de dependência acontece só na camada de composição (`config`), fora do domínio. Cada *port*
é pequena e específica por caso de uso (não uma interface genérica de repositório), e qualquer
implementação de uma *port* deve ser intercambiável sem quebrar quem a consome.

### Um único microserviço, um único banco de dados

O sistema é implementado como **um único microserviço** e **um único banco de dados** (PostgreSQL),
não uma decomposição em múltiplos serviços/bancos. Justificativa:

- O domínio tratado (receber, precificar e liquidar lotes de recebíveis) é um único contexto
  delimitado, não há indício, nas premissas de negócio levantadas, de subdomínios com ciclos de
  vida, times ou necessidades de escala independentes entre si a ponto de justificar o custo
  operacional de múltiplos serviços (descoberta de serviço, comunicação de rede, consistência
  eventual entre bases de dados separadas).
- Várias operações do sistema exigem **consistência forte e transação atômica entre entidades
  relacionadas** — por exemplo, salvar um lote, seus recebíveis e os eventos de auditoria
  correspondentes numa única transação (`@Transactional` no controller, cobrindo todos os `save()`
  do caso de uso). Particionar isso em serviços/bancos separados trocaria uma transação local
  simples por transação distribuída (saga, compensação) sem necessidade real para o problema atual.
- A arquitetura hexagonal já dá a modularidade interna (separação domínio/aplicação/adapters,
  extensão via novas implementações de *port*) que justificaria decompor em serviços, sem pagar o
  custo de infraestrutura distribuída antes de existir uma razão de negócio para isso.

### Banco relacional (PostgreSQL/SQL), não NoSQL

A escolha por um banco relacional é coerente com as características do domínio, já documentadas em
`SPEC.md`:

- **Dados fortemente relacionais e com integridade referencial exigida**: um lote tem N recebíveis
  (FK `lote_recebivel_id`), cada recebível referencia uma categoria de risco cadastrada
  (`categoria_risco.codigo`) e consulta uma taxa base por moeda (`taxa_base`), relações que um
  banco relacional garante nativamente via *foreign keys* e *constraints* (`ck_`, `uq_`, `fk_`),
  sem precisar de lógica de aplicação para manter consistência entre coleções.
- **Precisão decimal exata é um requisito não-negociável** (dinheiro e taxas nunca como
  `double`/`float`) — o tipo `numeric(19,2)`/`numeric(9,6)` do PostgreSQL mapeia diretamente para
  `BigDecimal` sem perda de precisão, algo que exigiria atenção extra em bancos NoSQL orientados a
  documento (tipicamente JSON, com representação numérica menos rígida).
- **Consistência forte e transações ACID são exigidas pelo domínio**: a persistência atômica de
  lote+recebíveis+eventos, o controle de concorrência via *optimistic locking* (coluna `version` em
  toda tabela de domínio) e o *lock* pessimista (`SELECT ... FOR UPDATE`) usado para garantir a
  idempotência da liquidação sob concorrência são padrões relacionais maduros, um modelo NoSQL
  tipicamente orientado a consistência eventual exigiria reconstruir essas garantias na aplicação.
- **Auditoria append-only** (`transacao_evento`, sem `UPDATE`/`DELETE`) se beneficia de um schema
  fixo e de índices relacionais (`ix_transacao_evento_ocorrido_em`) para consulta histórica, um
  caso de uso onde um banco relacional é uma escolha direta, sem necessidade da flexibilidade de
  schema que justificaria um documento NoSQL.

### Processamento síncrono, não assíncrono/orientado a eventos

Ao ser recebido, o lote é precificado **automaticamente e de forma síncrona, no mesmo caso de uso**
, não há fila, mensageria ou processamento em background em nenhum ponto do fluxo (nem para
precificação, nem para a chamada ao serviço externo de cotação de câmbio, cuja resiliência é
tratada com *circuit breaker* e *fallback* síncronos, não com retry assíncrono via fila). Motivos,
com base no que já está definido:

- **Não há etapa de aprovação no MVP** (`SPEC.md`, item 5) — o fluxo é "recebeu → precificou" dentro
  da mesma requisição HTTP; não existe um estado intermediário de espera que justificasse
  desacoplar produção e consumo via mensageria.
- **Os critérios de desempenho são definidos em termos de latência de requisição-resposta** (p95
  abaixo de 300ms para leituras e 500ms para escritas), um modelo síncrono é o que essa métrica
  pressupõe; um pipeline assíncrono mudaria a própria forma de medir o critério de aceite.
- **O volume por lote é limitado e a operação é auditável passo a passo**: cada recebível é
  processado individualmente dentro do laço de precificação (rejeitando itens inválidos sem abortar
  o lote), um padrão que se resolve bem numa única thread de requisição, sem necessidade de
  paralelismo distribuído.
- Fica registrado como direção natural de evolução (não uma decisão tomada agora): se o volume por
  lote ou a frequência de chamadas ao serviço externo de câmbio crescessem substancialmente, uma
  pipeline de precificação assíncrona (fila + *workers*) seria a extensão natural, a arquitetura
  hexagonal já isola essa possível mudança dentro da camada de aplicação/adapters, sem tocar no
  domínio.

### Frontend: SPA React desacoplada da API por contrato

O frontend é uma *Single Page Application* (React 19.3 + TypeScript), com o estado de servidor
(cache, *loading*, revalidação) isolado via TanStack Query e o estado de formulário via React Hook
Form + Zod, organizada por *feature* (não por tipo técnico). Os tipos de request/response da API
são gerados a partir do `openapi.yaml` (nunca escritos à mão), o que mantém o contrato entre
frontend e backend explícito e verificável — qualquer mudança de schema no backend se propaga para
erros de tipo no frontend em vez de falhas silenciosas em runtime.

## Dependências e Riscos

### Dependências externas

| Dependência | Papel | Observação |
|---|---|---|
| PostgreSQL 18 | Única base de persistência do sistema | Ponto único de consistência forte; também ponto único de falha (sem réplica/HA configurada) |
| Serviço de cotação de câmbio (HTTP externo) | Fonte da cotação usada na conversão cross-currency | Em desenvolvimento, atendido por um mock (WireMock); resiliência via *circuit breaker* + *fallback* (ver `SPEC.md`, item 11) |
| Docker / Docker Compose | Ambiente oficial de build, execução e testes de integração (Testcontainers) | Ambiente de desenvolvimento sem Maven/Node/JDK instalados localmente depende inteiramente disso |
| Spring Boot 4.1 / Java 25 | Framework e runtime do backend | Versões recentes ("bleeding-edge"); geraram múltiplas incompatibilidades já documentadas no `PROGRESS.md` (reestruturação de autoconfiguração, módulo de teste do MockMvc, migração para Jackson 3) |
| GitHub Actions | Pipeline de CI (build + testes + build de imagem, sem push/deploy) | Sem CD — nenhuma automação de publicação/deploy existe hoje |

### Riscos identificados

- **Ausência de autenticação/autorização** (`DECISIONS.md`, item 1): decisão consciente para o
  escopo do desafio, mas é um risco real caso o ambiente deixe de ser isolado. Mitigação já
  presente: API mantida *stateless* e desacoplada, permitindo adicionar validação de token
  OAuth2/OIDC como filtro na borda sem alterar o domínio.
- **Origem da cotação de câmbio não é persistida no recebível** (`SPEC.md`, item 11): decisão
  consciente de manter o modelo simples (visibilidade só via log/métrica), implica que, olhando
  só a persistência depois do fato, não é possível reconstruir se uma cotação específica veio do
  serviço externo, do cache ou do valor de *fallback* estático.
- **Dependência de versões recentes do ecossistema** (Spring Boot 4.1, Java 25, TypeScript):
  múltiplas quebras de compatibilidade já enfrentadas e documentadas no `PROGRESS.md` (ex.:
  evitou-se deliberadamente adicionar Resilience4j pelo risco de incompatibilidade com o Boot 4.1).
  Risco de continuar exigindo atenção extra em futuras atualizações de dependências.
- **Sem TLS, sem RBAC, sem mascaramento de dado sensível em log, sem trilha de auditoria imutável a
  nível de infraestrutura** ,lacunas já listadas explicitamente no `README.md` ("Escopo e próximos
  passos") como necessárias para uma versão de produção, fora do escopo atual.
- **Ambiente de desenvolvimento não é idêntico ao de CI/produção**: o ambiente local (Windows +
  Docker Desktop) exigiu contornos específicos (variáveis do Testcontainers, *workaround* de
  *bind mount* cross-OS para o Vitest) que não se aplicam ao runner Linux do GitHub Actions — risco
  de divergência de comportamento entre "funciona localmente" e execução real de CI, mitigado
  parcialmente ao rodar os mesmos comandos nativamente (sem *bind mount*) antes de cada commit.
- **Banco de dados único como ponto único de falha**: não há réplica, *failover* automático ou
  estratégia de backup documentada — qualquer indisponibilidade do PostgreSQL indisponibiliza todo
  o sistema (consequência direta da escolha de um único banco, seção "Direcionamento de
  arquitetura").

## Requisitos funcionais e não funcionais identificados

### Requisitos funcionais

1. Receber um lote de recebíveis via API REST (`POST /api/v1/lotes-recebiveis`), com os dados do
   lote e a lista de recebíveis (`ativo`, `valorBruto`, `dataVencimento`, `categoriaRisco`,
   `moedaPagamento` opcional).
2. Precificar automaticamente cada recebível do lote, calculando o deságio pelo método de desconto
   composto por valor presente (taxa base da moeda + spread de risco da categoria + custo
   operacional fixo, todos ao mês).
3. Rejeitar individualmente recebíveis com regra de negócio violada (ex.: data de vencimento não
   posterior à data de referência), sem abortar o processamento do restante do lote.
4. Converter o valor presente para a moeda de pagamento quando esta for diferente da moeda do ativo
   (sempre BRL), usando a cotação de câmbio vigente — sem converter o deságio.
5. Registrar de forma auditável e imutável (append-only) cada evento relevante: lote recebido, lote
   precificado/erro, recebível precificado/rejeitado/liquidado.
6. Consultar lotes de recebíveis: listagem paginada e ordenável, e detalhe de um lote específico
   com seus recebíveis.
7. Liquidar (pagar) um recebível já precificado, de forma idempotente — repetir a mesma requisição
   não deve gerar uma segunda liquidação nem duplicar o evento de auditoria correspondente.
8. Buscar a cotação de câmbio usada na conversão cross-currency num serviço HTTP externo, com
   estratégia de resiliência (cache do último valor bom e valor de segurança estático) caso esse
   serviço esteja indisponível.
9. Expor a documentação da API via OpenAPI/Swagger.
10. Expor métricas de negócio (lotes precificados, recebíveis processados/liquidados, valores
    processados) e de infraestrutura (HTTP, JVM, pool de conexões), visualizáveis num dashboard
    Grafana provisionado.
11. Permitir, via frontend, cadastrar um lote de recebíveis (formulário com múltiplos itens),
    listar lotes existentes, visualizar o detalhe de um lote e liquidar recebíveis precificados.

### Requisitos não funcionais

- **Usabilidade**: clareza e transparência de valores/taxas/prazos, formatação monetária correta;
  prevenção de erros em operações críticas (confirmação, revisão de dados, possibilidade de
  cancelar); feedback imediato do resultado de cada operação; consistência terminológica e de
  fluxo, alinhada a normas do setor bancário.
- **Segurança**: nenhuma proteção (autenticação/autorização) implementada nas APIs, por decisão
  consciente de escopo (`DECISIONS.md`, item 1) — API mantida *stateless*/desacoplada para permitir
  adicionar validação de token via OAuth2/OIDC como filtro na borda no futuro, sem impacto no
  domínio.
- **Desempenho**: latência de API p95 abaixo de 300ms (leituras) e 500ms (escritas) em ambiente
  local; frontend com primeira renderização útil (FCP) abaixo de 1,5s e bundle inicial abaixo de
  500 KB (com *lazy loading* por rota); nenhuma consulta N+1 nas listagens, toda listagem paginada
  e com índices nos campos de filtro/ordenação; suportar ao menos 1000 requisições simultâneas sem
  erro ou degradação acima dos limites de latência.
- **Precisão numérica**: valores monetários e taxas sempre em `BigDecimal` (nunca `double`/`float`);
  arredondamento HALF_EVEN para 2 casas decimais aplicado apenas no resultado final; no transporte
  (JSON), dinheiro e taxas sempre como *string*, nunca `number`.
- **Auditabilidade**: eventos de transação append-only (sem `UPDATE`/`DELETE`); taxas, spread e
  cotação de câmbio "congelados" (snapshot) no momento da precificação de cada recebível, para
  reconstituir o cálculo exato mesmo que os valores de referência mudem depois.
- **Idempotência e consistência sob concorrência**: a liquidação de um recebível é idempotente por
  desenho (endpoint `PUT`, verificação de estado, *lock* pessimista na busca) — repetição de
  requisição (retry de rede, duplo clique) não gera efeito duplicado.
- **Resiliência**: dependência externa (serviço de cotação de câmbio) protegida por *circuit
  breaker* e *fallback* em cascata (cache do último valor bom → valor estático de segurança), para
  que uma indisponibilidade externa nunca impeça a precificação de um lote.
- **Observabilidade**: métricas de negócio e infraestrutura expostas via Micrometer/Prometheus,
  visualizáveis em dashboard Grafana; todo log de requisição carrega um `X-Correlation-Id`
  (gerado se ausente) propagado via MDC, permitindo correlacionar todas as linhas de uma mesma
  requisição.
- **Manutenibilidade e testabilidade**: arquitetura hexagonal rigorosa (domínio livre de
  dependência de framework/infraestrutura, injeção de dependência só na camada de composição);
  cada classe/módulo com responsabilidade única; extensão via novas implementações de *port*, não
  via modificação de código existente; testes unitários para domínio/aplicação e testes de
  integração para adapters (rodando em Docker via Testcontainers).
- **Portabilidade de ambiente**: todo o sistema (build, execução, testes) roda via Docker/Docker
  Compose, sem exigir Maven/Node/JDK instalados localmente.
- **Qualidade de processo**: GitHub Flow (branches curtas por funcionalidade, integração via Pull
  Request); pipeline de CI (build + testes de backend e frontend + build de imagem Docker, sem
  publicação/deploy) obrigatório antes do merge na `main`.
