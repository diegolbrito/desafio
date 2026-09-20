# SPEC.md — SRM Credit Engine

## Visão geral

A SRM Asset é referência em fundos de investimento, especialmente FIDCs (Fundos de Investimento em
Direitos Creditórios). A operação consiste em adquirir ativos (duplicatas, contratos, recebíveis)
de empresas cedentes, provendo liquidez ao mercado.

Com a globalização do portfólio, o fundo passou a operar com caixa multimoedas (BRL e USD). A mesa
de operações precisa de um sistema — o **SRM Credit Engine** — responsável por precificar e liquidar
esses ativos com segurança e precisão decimal.

**Problema a resolver:** receber um lote de recebíveis, calcular o deságio (desconto) com base no
risco do ativo e na moeda de pagamento, e registrar a transação de forma auditável.

## Premissas adotadas

> Decisões tomadas na ausência de resposta do negócio, para viabilizar a implementação do desafio.
> Tratadas como fonte de verdade equivalente ao restante deste documento.

### 1. Fórmula de cálculo do deságio
- Método: **desconto composto por valor presente**, padrão de precificação de ativos de renda fixa.
- Taxa de desconto anual = `taxaBase(moeda) + spreadRisco(categoria) + custoOperacional (fixo)`.
- Prazo: dias corridos entre a data de precificação (data de entrada do lote) e a data de vencimento do recebível. Sem calendário de dias úteis/feriados no MVP (simplificação assumida).
- Base de dias por moeda (convenção de mercado):
  - BRL: base **252** (convenção local, taxas prefixadas referenciadas ao CDI).
  - USD: base **360** (convenção internacional Actual/360).
- Fórmula:
  - `fatorDesconto = (1 + taxaDesconto) ^ (prazoDias / baseDias)`
  - `valorPresente = valorBruto / fatorDesconto`
  - `deságio = valorBruto − valorPresente`
- Precisão: cálculos intermediários em `BigDecimal` com `MathContext` de alta precisão (ex.: `DECIMAL128`); arredondamento HALF_EVEN para 2 casas decimais aplicado apenas no resultado final (`valorPresente`, `deságio`), conforme a seção "Decisões de precisão numérica".

### 2. Classificação de risco e spread
- Categorias de risco por recebível (escala simplificada de 6 níveis, inspirada na lógica de rating de crédito de mercado):

  | Categoria | Spread de risco (a.a.) |
  |---|---|
  | AA | 1,0% |
  | A  | 2,0% |
  | B  | 3,5% |
  | C  | 5,5% |
  | D  | 8,0% |
  | E  | 12,0% |

- A categoria de risco é **informada na entrada do lote** (campo obrigatório por recebível), não é calculada/derivada de bureau externo no MVP.
- Cadastrada em tabela de referência (`categoria_risco`), populada via migration Flyway (seed), permitindo evolução futura sem alterar código.

### 3. Taxa base e câmbio
- Taxa base por moeda é um proxy de mercado (CDI para BRL, SOFR para USD), cadastrado em tabela de referência (`taxa_base`), com valor vigente definido via seed/migration — sem integração automática com fonte externa no MVP.
- Valores seed assumidos: BRL = 10,65% a.a.; USD = 4,80% a.a.
- **Câmbio (conversão BRL↔USD) não é necessário para o cálculo do deságio.** Cada recebível é precificado e permanece na sua própria moeda de origem — o fundo opera caixa segregado por moeda ("caixa multimoedas" = posições paralelas, não conversão). Conversão cambial fica fora do escopo do MVP (útil apenas para relatórios gerenciais consolidados, não implementados).
- Taxa base e spread de risco aplicados a cada recebível são "congelados" (snapshot) no momento da precificação e registrados na transação, garantindo auditabilidade mesmo que os valores de referência mudem depois.

### 4. Entrada do lote
- Entrada via API REST (`POST /api/v1/lotes-recebiveis`), payload com os dados do lote e a lista de recebíveis.
- Campos do recebível: `cedente` (texto livre/referência — ver item 7), `valorBruto`, `moeda`, `dataVencimento`, `categoriaRisco`.
- Custo operacional aplicado como spread fixo adicional (valor assumido: 0,5% a.a.), configurado na aplicação (não em banco), por ser parâmetro estável.

### 5. Fluxo de aprovação
- **Não há etapa de aprovação** (nem em lote, nem item a item) no MVP. Ao ser recebido, o lote é precificado automaticamente (síncrono, no mesmo caso de uso).
- Status do lote: `RECEBIDO` → `PRECIFICADO` (ou `ERRO` em falha de validação/cálculo).
- Validação estrutural do payload (Bean Validation) é all-or-nothing (400 se inválido). Erros de regra de negócio por recebível (ex.: data de vencimento no passado) rejeitam apenas aquele item — não abortam o lote inteiro; cada recebível carrega seu próprio status (`PRECIFICADO` / `REJEITADO`) e motivo.

### 6. Escopo: liquidação fora do MVP
- Fluxo implementado: **cadastrar lote → precificar (calcular deságio) → registrar transação de forma auditável**.
- Aprovação, liquidação/pagamento ao cedente, liquidação parcial, cancelamento e recompra ficam **fora do escopo** deste desafio.

### 7. Cadastro de cedente
- Cedente é um campo de referência (texto) no recebível, **sem entidade/cadastro próprio** no MVP.

### 8. Escala decimal de valores monetários
- A seção "Decisões de precisão numérica" (2 casas decimais) e a tabela "Tipos de dados canônicos" (`numeric(19,2)`) estavam em contradição. Decisão: prevalece **2 casas decimais** (`numeric(19,2)`) para todo valor monetário, em todas as camadas — inclusive `valorPresente` e `deságio` calculados. A tabela de tipos canônicos e o exemplo de campo foram corrigidos para `numeric(19,2)` / `"15000.00"`.
- Cálculos intermediários usam `BigDecimal` com `MathContext` de alta precisão (sem arredondar); o arredondamento HALF_EVEN para 2 casas ocorre apenas ao fixar o resultado final (`valorPresente`, `deságio`) antes de persistir/retornar.

## Decisões de precisão numérica

- Banco de dados: Campos monetários devem usar o tipo `DECIMAL`
- Backend: Precisão numérica: `BigDecimal`
- Arredondamento: half-even, precisão de 2 casas decimais, ocorre sempre no resultado final

## Critérios de aceite

**Usabilidade do sistema**:
- Clareza e transparência das informações, valores, taxas, saldos e prazos apresentados de forma legível, formatação monetária correta, destaque para o que impacta o usuário.
- Prevenção de erros em operações críticas, confirmação antes de operações, revisão dos dados (valor, data) e possibilidade de cancelar a operação.
- Feedback imediato, confirmação clara do resultado de cada operação (sucesso, pendente, falha).
- Consistência e conformidade regulatória, terminologia, fluxos e layout padronizados, alinhados a exigências como acessibilidade e normas do setor bancário

**Segurança**:
- No momento não irei utilizar proteção nas APIs do sistema

**Desempenho**:
- Latência de API: p95 abaixo de 300 ms para leituras e 500 ms para escritas em ambiente local, medido nos endpoints principais.
- Tempo de carregamento do frontend: primeira renderização útil (FCP) abaixo de 1,5s e bundle inicial abaixo de 500 KB, com lazy loading das demais rotas.
- Banco de dados: nenhuma consulta N+1 nas listagens; toda listagem paginada (padrão 20–50 itens) e com índices nos campos de filtro/ordenação.
- Concorrência: suportar pelo menos 1000 requisições simultâneas sem erro ou degradação acima dos limites de latência.

## Estrutura do projeto

- Código Backend e Frontend devem ficar projetos separados dentro da pasta raiz

## Convenções de código

### Github
- commits padronizados
- Nunca realizar commit direto na master, utilize github flow

### Nomenclatura (regra transversal)
| Camada        | Padrão            | Exemplo                 |
|---------------|-------------------|-------------------------|
| Banco (tabela/coluna) | snake_case, singular p/ tabela | `recebivel`, `valor_bruto` |
| Java (classe/campo)   | PascalCase / camelCase  | `Recebivel`, `valorBruto`  |
| JSON da API           | camelCase               | `valorBruto`               |
| React (componente/prop/state) | PascalCase / camelCase | `RecebivelCard`, `valorBruto` |
| Enums                 | UPPER_SNAKE em todas as camadas | `PENDENTE`, `LIQUIDADO` |

- Idioma dos nomes de domínio: português (`cedente`, `desagio`, `lote`); nomes técnicos em inglês (`createdAt`, `id`, `status`).
- Nunca abreviar (`valor`, não `vlr`; `quantidade`, não `qtd`).
- Booleanos com prefixo: `ativo`, `is...` não é usado.

### Tipos de dados canônicos
| Tipo de dado      | PostgreSQL       | Java              | JSON                     | React/TS          |
|-------------------|------------------|-------------------|--------------------------|-------------------|
| Identificador     | `uuid`           | `UUID`            | string                   | `string`          |
| Valor monetário   | `numeric(19,2)`  | `BigDecimal`      | string `"1234.56"`     | `string` (formatar só na exibição) |
| Taxa / percentual | `numeric(9,6)`   | `BigDecimal`      | string (fração decimal, `"0.02"`) | `string` |
| Moeda             | `char(3)`        | `enum Moeda`      | `"BRL"` / `"USD"`        | `'BRL' \| 'USD'`   |
| Data              | `date`           | `LocalDate`       | `"2026-09-17"`           | `string`          |
| Data/hora         | `timestamptz`    | `OffsetDateTime`  | ISO-8601 com offset, sempre em UTC | `string`  |
| Texto curto       | `varchar(N)`     | `String`          | string                   | `string`          |
| Status            | `varchar(30)` + check constraint | `enum` | string UPPER_SNAKE    | union type        |

- Dinheiro e taxas **nunca** como `double`/`float`/`number`. No JSON trafegam como string para preservar precisão.

### Banco de dados
- Toda tabela tem: `id uuid PK default gen_random_uuid()`, `created_at timestamptz not null default now()`, `updated_at timestamptz not null`, `version integer not null default 0` (optimistic lock).
- Nada é deletado fisicamente em tabelas de domínio: `deleted_at timestamptz null`.
- FKs nomeadas `<tabela>_id`; índices nomeados `ix_<tabela>_<colunas>`; constraints `ck_`, `uq_`, `fk_`.
- Colunas `not null` por padrão; nulo só quando semanticamente "desconhecido".
- Migrations via Flyway, arquivos `V<yyyyMMddHHmm>__descricao.sql`. Sem DDL fora de migration.
- Auditoria: tabelas de evento (`transacao_evento`) são append-only, sem `UPDATE`/`DELETE`.

### API (REST)
- Base path `/api/v1`. Recursos no plural, kebab-case: `/api/v1/lotes-recebiveis/{id}`.
- Verbos: `GET` lista/detalhe, `POST` cria, `PUT` substitui, `PATCH` parcial, `DELETE` soft delete.
- Request/response com DTOs próprios (`XxxRequest`, `XxxResponse`); entidades JPA nunca expostas.
- Paginação: `?page=0&size=20&sort=createdAt,desc`; resposta `{ content, page, size, totalElements, totalPages }`.
- Erros seguem RFC 9457 (Problem Details): `{ type, title, status, detail, instance, errors: [{ field, message }] }`.
- Códigos: 200/201/204 sucesso; 400 validação; 404 não encontrado; 409 conflito (versão/duplicidade); 422 regra de negócio.
- Validação com Bean Validation nos DTOs; mensagens em português.
- Cabeçalhos obrigatórios: `X-Correlation-Id` (gerado se ausente, propagado nos logs).
- Datas/horas sempre UTC no transporte; conversão de fuso só no front.

### Frontend (React + TypeScript)
- TypeScript estrito; tipos de API gerados a partir do OpenAPI (nunca escritos à mão).
- Estrutura por feature: `src/features/<feature>/{components,hooks,api,types}`.
- Componentes: função + hooks; um componente exportado por arquivo, arquivo com o mesmo nome.
- Estado de servidor via TanStack Query; estado de formulário via React Hook Form + Zod (schema espelha a validação da API).
- Formatação de valores só na camada de exibição: `Intl.NumberFormat('pt-BR', { style: 'currency', currency })`; o valor em estado permanece string.
- Nomes de handlers `handleXxx`, props de callback `onXxx`.
- Textos de UI centralizados (i18n ou `constants`), sem string literal em JSX.

### Exemplo completo de um campo (referência)
Campo "valor bruto do recebível":
- DB: `recebivel.valor_bruto numeric(19,2) not null check (valor_bruto > 0)`
- Java: `private BigDecimal valorBruto;` + `@NotNull @Positive` no DTO
- JSON: `"valorBruto": "15000.00"`
- TS: `valorBruto: string` → exibido como `R$ 15.000,00`

## Arquitetura Backend e decisões
- **Apenas um microserviço**
- **Stack tecnológico**: Java 25 LTS + Spring Boot 4.1 + build Maven + banco de dados PostgresQL
- **Arquitetura**: Aplique arquitetura hexagonal (ports & adapters) de forma rigorosa:
- **Domínio**: entidades, value objects, regras de negócio e exceções de domínio. Sem dependência de framework, banco, HTTP ou qualquer biblioteca de infraestrutura.
- **Aplicação**: casos de uso / serviços de aplicação que orquestram o domínio, expondo ports de entrada e dependendo apenas de ports de saída definidas como interfaces.
- **Adapters**: implementações concretas das ports: HTTP/REST (entrada), persistência, mensageria, clientes externos (saída). Os adapters dependem do núcleo; o núcleo nunca depende dos adapters.
- A injeção de dependência deve ser feita na camada de composição (entrypoint), fora do domínio.
- Estruture as pastas de modo que a direção das dependências fique evidente e seja verificável.
- Banco de dados: PostgreSQL 18

**Padrão de código**: Ao gerar o código, respeite explicitamente:
- Cada classe/módulo com uma única responsabilidade, evite classes grandes e services genéricos.
- Extensão via novas implementações de ports, não via modificação de código existente.
- Implementações de uma port devem ser intercambiáveis sem quebrar quem as consome.
- Ports pequenas e específicas por caso de uso, em vez de interfaces genéricas e inchadas.
- O domínio e a aplicação dependem de abstrações, nunca de classes concretas de infraestrutura.
- Testes unitários para o domínio e casos de uso.
- Testes de integração para os adapters, rodando dentro do Docker.
- Trate erros de forma explícita, com mapeamento de exceções de domínio para respostas HTTP adequadas.

**Logging**:
- **API**: código da aplicação usa sempre `org.slf4j.Logger`/`LoggerFactory` (nunca classes do
  Logback diretamente) — é o padrão do Spring Boot e mantém o domínio/aplicação livres de
  acoplamento a uma implementação concreta de logging.
- **Backend**: [Logback](https://logback.qos.ch/), o padrão do Spring Boot (já vem via
  `spring-boot-starter-logging`, sem dependência extra no `pom.xml`). Escolhido por ser a opção
  "batteries included" do framework — zero configuração de dependências, boa integração com
  `springProperty`/`springProfile` no XML, e suficiente para as necessidades deste projeto (não
  há requisito de throughput que justifique trocar por uma alternativa).
- **Saída**: exclusivamente console/stdout (`ConsoleAppender`), sem arquivo de log. Em container,
  a coleta é responsabilidade do runtime/orquestrador (Docker, Kubernetes, etc.), não da
  aplicação — mesmo raciocínio de "nada é escrito em disco pela aplicação" já usado para outros
  aspectos deste projeto.
- **Configuração**: `backend/src/main/resources/logback-spring.xml` (nome com sufixo `-spring` de
  propósito, para habilitar `springProperty`/`springProfile`, que só funcionam nesse arquivo).
- **Níveis por pacote/biblioteca**:
  - Pacote raiz da aplicação (`com.srmasset.creditengine`): `DEBUG`.
  - `org.springframework`, `org.hibernate`, `com.zaxxer.hikari`, `org.postgresql`: `WARN`
    (bibliotecas de infraestrutura só devem aparecer no log quando algo sai do esperado).
  - Root: `INFO`.
- **Onde loga cada camada**:
  - **Startup**: um `INFO` após `ApplicationReadyEvent`, com perfil ativo e porta.
  - **Entrada HTTP**: um único ponto central (`CorrelationIdFilter`, adapter de entrada) loga
    método, rota, status e duração de cada requisição em `INFO` — controllers individuais nunca
    duplicam esse log.
  - **Aplicação/casos de uso**: início e resultado das operações de negócio principais em `INFO`
    (ex.: `PrecificarLoteService`), decisões/rejeições pontuais em `DEBUG`. Nunca loga o valor de
    campos de negócio (cedente, valores monetários) nessas linhas — só identificadores, contagens
    e status.
  - **Persistência/JPA**: a aplicação não loga SQL nem erros de conexão manualmente — Hibernate
    já expõe isso via `org.hibernate.SQL`/`org.hibernate.orm.jdbc.bind` (só no perfil `dev`), e
    falhas de acesso a dados que não forem tratadas como regra de negócio sobem até o
    `GlobalExceptionHandler`, que loga o erro uma única vez. Adapters de persistência não
    implementam "loga e relança".
  - **Erros/exceções**: centralizados no `GlobalExceptionHandler`. Erros 4xx (validação, regra de
    negócio violada, recurso não encontrado, conflito de versão) em `WARN`, sem stacktrace — são
    esperados/causados pelo cliente, não falhas do sistema. Erros 5xx em `ERROR`, com a exceção
    completa como último argumento do log. Cada erro é logado uma única vez, no handler; camadas
    inferiores não devem logar a mesma exceção antes de relançá-la.
- **Correlation id / MDC**: todo log dentro do ciclo de uma requisição carrega
  `X-Correlation-Id` (gerado se ausente — ver `CorrelationIdFilter`) via MDC (`%X{correlationId}`
  no pattern), permitindo juntar todas as linhas de uma mesma requisição. O MDC é sempre limpo no
  `finally` do filtro, mesmo em caso de exceção — importante para não vazar contexto entre
  requisições caso a aplicação venha a rodar em virtual threads no futuro.
- **Dados sensíveis**: nunca logar senhas, tokens, CPF/CNPJ, nem dados bancários ou de cartão. Como
  este projeto não lida com esses dados hoje, a regra prática é: valores monetários e identificação
  do cedente só aparecem na resposta HTTP, nunca em linha de log.
- **SQL em desenvolvimento**: dentro de `<springProfile name="dev">` no `logback-spring.xml`, o
  logger `org.hibernate.SQL` fica em `DEBUG` (mostra a query) e `org.hibernate.orm.jdbc.bind` em
  `TRACE` (mostra os parâmetros vinculados). Ativa automaticamente ao subir com
  `SPRING_PROFILES_ACTIVE=dev` ou `--spring.profiles.active=dev`, sem editar o arquivo. Por
  logar os parâmetros de verdade, esse nível **nunca** deve ser usado em produção.

## Arquitetura Frontend e decisões

Construa uma Single Page Application seguindo boas práticas modernas:
- **Comunicação front-back**: REST
- **Stack tecnológico**: React 19.3 + TypeScript
- **Organização por features**: estruture o código por domínio/funcionalidade (`features/<nome>`), não por tipo técnico. Cada feature contém seus componentes, hooks/composables, serviços e testes. Código compartilhado fica em `shared/` (UI genérica, utilitários, tipos).
- **Separação de camadas**:
  - Componentes de apresentação puros (recebem props, não conhecem API nem estado global).
  - Lógica de estado e efeitos isolada em hooks/composables ou stores.
  - Camada de acesso à API isolada (client HTTP + serviços tipados por recurso), com DTOs e mapeamento para modelos de UI. Nenhum componente chama `fetch`/`axios` diretamente.
  - Contratos de API (tipos das requisições/respostas) definidos em um único lugar e alinhados com o backend.
- **Gerenciamento de estado**: separe estado de servidor (cache de dados remotos, loading, erro, revalidação) de estado de UI/cliente. Não centralize tudo em um store global; use estado local sempre que for suficiente.
- **Roteamento**: rotas declaradas em um único lugar, com lazy loading por rota/feature e proteção de rotas quando houver autenticação.
- **Tratamento de erros e estados**: todo fluxo de dados deve tratar explicitamente loading, vazio, erro e sucesso; error boundaries para falhas inesperadas; feedback ao usuário para ações (sucesso/erro).
- **Formulários**: validação declarativa com schemas reutilizáveis, mensagens de erro claras e proteção contra submissão dupla.
- **Design system mínimo**: componentes base reutilizáveis (botão, input, tabela, modal, etc.) em `shared/ui`, com tokens de tema centralizados. Evite estilos duplicados espalhados.
- **Acessibilidade**: HTML semântico, labels em campos, navegação por teclado e atributos ARIA onde necessário.
- **Qualidade**: ESLint + Prettier configurados; testes unitários de componentes e hooks (foco em comportamento, não em implementação).
- **Configuração**: URL da API e demais variáveis via ambiente em build/runtime, nunca hardcoded.

## Build & Deploy
- **Ambiente**: Docker Desktop 4.8x / Docker Compose v2
