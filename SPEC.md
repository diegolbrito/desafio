# SPEC.md - Credit Engine

## Visão geral

A empresa é referência em fundos de investimento, especialmente FIDCs (Fundos de Investimento em
Direitos Creditórios). A operação consiste em adquirir ativos (duplicatas, contratos, recebíveis)
de empresas cedentes, provendo liquidez ao mercado.

Com a globalização do portfólio, o fundo passou a operar com caixa multimoedas (BRL e USD). A mesa
de operações precisa de um sistema — o **  Credit Engine** — responsável por precificar e liquidar
esses ativos com segurança e precisão decimal.

**Problema a resolver:** receber um lote de recebíveis, calcular o deságio (desconto) com base no
risco do ativo e na moeda de pagamento, e registrar a transação de forma auditável.

## Premissas adotadas

> Decisões tomadas na ausência de resposta do negócio, para viabilizar a implementação do desafio.
> Tratadas como fonte de verdade equivalente ao restante deste documento.

### 1. Fórmula de cálculo do deságio
- Método: **desconto composto por valor presente**, padrão de precificação de ativos de renda fixa.
- Taxa de desconto **mensal** = `taxaBase(moeda) + spreadRisco(categoria) + custoOperacional (fixo)` — todas as três parcelas expressas ao mês (a.m.).
- Prazo: em **meses inteiros** entre a data de precificação (data de entrada do lote) e a data de vencimento do recebível. Mês incompleto conta como mês inteiro (arredondamento para cima — o mês iniciado é cobrado por inteiro, convenção usual em desconto de recebíveis). Sem calendário de dias úteis/feriados no MVP (simplificação assumida).
- Juros **compostos mensais**: capitalização por mês corrido, expoente inteiro igual ao prazo em meses (sem base de dias por moeda — a distinção BRL/USD fica só na taxa base de referência de cada moeda).
- Fórmula:
  - `fatorDesconto = (1 + taxaDesconto) ^ prazoMeses`
  - `valorPresente = valorBruto / fatorDesconto`
  - `deságio = valorBruto − valorPresente`
- Precisão: cálculos intermediários em `BigDecimal` com `MathContext` de alta precisão (ex.: `DECIMAL128`); arredondamento HALF_EVEN para 2 casas decimais aplicado apenas no resultado final (`valorPresente`, `deságio`), conforme a seção "Decisões de precisão numérica".

### 2. Classificação de risco e spread
- Categorias de risco por recebível (escala simplificada de 6 níveis, inspirada na lógica de rating de crédito de mercado). Spreads originalmente cotados ao ano (referência de mercado) e convertidos ao equivalente mensal — `taxaMensal = (1 + taxaAnual) ^ (1/12) − 1` — para compor a taxa de desconto mensal do item 1:

  | Categoria | Spread de risco (a.a. de referência) | Spread de risco aplicado (a.m.) |
  |---|---|---|
  | AA | — (fixado, ver nota) | 1,5000% |
  | A  | 2,0%  | 0,1652% |
  | B  | 3,5%  | 0,2871% |
  | C  | — (fixado, ver nota) | 2,5000% |
  | D  | 8,0%  | 0,6434% |
  | E  | 12,0% | 0,9489% |

  > **Nota**: AA e C foram fixados diretamente em `0,015000`/`0,025000` (migration `V202609181007`) para reproduzir 3 casos de aferição fornecidos pelo negócio (`CasosAfericaoTest`) — com taxa base de 1% a.m. (ver abaixo), somam exatamente 2,5% a.m. (AA) e 3,5% a.m. (C), as taxas de desconto usadas nesses casos. As demais categorias mantêm a conversão do valor anual de referência do item 1.

- A categoria de risco é **informada na entrada do lote** (campo obrigatório por recebível), não é calculada/derivada de bureau externo no MVP.
- Cadastrada em tabela de referência (`categoria_risco`), populada via migration Flyway (seed), permitindo evolução futura sem alterar código.

### 3. Taxa base e câmbio
- Taxa base por moeda é um proxy de mercado (CDI para BRL, SOFR para USD), cadastrado em tabela de referência (`taxa_base`), com valor vigente definido via seed/migration — sem integração automática com fonte externa no MVP.
- Valor seed: **1% a.m. para as duas moedas** (BRL e USD), fixado na migration `V202609181007` — simplificação que substitui os proxies CDI/SOFR convertidos usados antes (10,65%/4,80% a.a.), adotada para reproduzir os mesmos casos de aferição do item 2 (taxa de desconto = taxaBase + spreadRisco).
- **O ativo é sempre denominado em BRL.** Não existe mais campo `moeda` na entrada do recebível — quem cria o lote (via API ou frontend) só escolhe a `moedaPagamento`. Essa é uma simplificação deliberada do modelo original (que permitia ativo em BRL ou USD): o fundo sempre origina/registra o ativo em reais; a variabilidade de moeda fica inteiramente do lado do pagamento. `Moeda.BRL` é atribuída automaticamente ao ativo na criação do domínio (`Recebivel.criar`), não é mais um dado de entrada — a coluna `moeda` continua existindo no banco (histórico + uso interno para consultar `taxaBase`), mas a aplicação nunca mais grava um valor diferente de `BRL` nela. Linhas antigas com `moeda = 'USD'` (de antes desta decisão) não foram reescritas — são histórico válido de um período em que a regra ainda não existia.
- **Câmbio cross-currency (ativo em BRL, pagamento em outra moeda)**: o cálculo do deságio é feito inteiramente em BRL (taxaBase/spread) — nunca depende de câmbio. Quando o pagamento ocorre numa moeda diferente de BRL (campo `moedaPagamento` informado e diferente de BRL), o `valorPresente` é convertido usando a cotação vigente do sistema — buscada num serviço externo, com estratégia de resiliência caso ele esteja fora do ar (ver item 11). A conversão acontece **ao final**, e **só sobre o `valorPresente`**: primeiro calcula-se `valorPresente`/deságio normalmente em BRL, e só depois o `valorPresente` é convertido para `moedaPagamento` — representa o valor que de fato será pago, na moeda do pagamento. Como o ativo é sempre BRL, a única direção de conversão possível é BRL → moeda de pagamento (nunca o contrário).
  - **`deságio` nunca é convertido** — permanece sempre na moeda do título, mesmo quando há cross-currency. Ele representa o custo do desconto no referencial do próprio título (quanto se "perdeu" em relação ao valor de face), não um valor a pagar; converter junto não teria significado de negócio. Validado com casos de aferição fornecidos pelo negócio (ver `CasosAfericaoTest`): um título de R$ 100.000,00/3 meses gera o mesmo deságio em BRL (R$ 7.140,06) esteja ele sendo pago em BRL ou em USD — só o `valorPresente` muda (R$ 92.859,94 vira US$ 17.094,67 à cotação 5,4321).
  - **Consequência**: o invariante `valorPresente + deságio == valorBruto` só vale quando **não** há cross-currency (mesma moeda). Com conversão, `valorPresente` fica na moeda de pagamento e `valorBruto`/`deságio` continuam na moeda do título — a soma direta dos dois não faz sentido nesse caso (moedas diferentes) e não é mais um invariante esperado.
  - **Cotação como parâmetro de configuração da aplicação** (`credit-engine.cotacao-cambio`, env var `COTACAO_CAMBIO`), mesmo padrão de `custoOperacional` (item 4) — **não** é recebida via API/por recebível. Diferente de `taxaBase`/`spreadRisco` (que também são configuração, mas cadastradas em tabela de referência), a cotação é um único valor de aplicação porque só existem duas moedas (um único par a converter); não há necessidade de tabela de referência para um valor só.
  - Convenção: `cotacaoCambio` é sempre expressa como "quantidade de BRL por 1 USD" (padrão de mercado, ex. PTAX), independente de qual das duas moedas é o título — evita ambiguidade de direção. Simplificação válida enquanto só existem duas moedas (BRL/USD); um terceiro par exigiria revisar essa convenção (provavelmente migrando para tabela de referência por par de moedas).
  - Moeda de pagamento e a cotação efetivamente usada são "congeladas" (snapshot) no recebível no momento da precificação, pelo mesmo motivo de auditabilidade da taxa base/spread — mesmo sendo hoje um parâmetro único de aplicação, ele pode mudar entre uma precificação e outra (redeploy com novo valor), então o snapshot por recebível continua sendo o que garante a reconstituição exata do cálculo passado.
- Taxa base e spread de risco aplicados a cada recebível são "congelados" (snapshot) no momento da precificação e registrados na transação, garantindo auditabilidade mesmo que os valores de referência mudem depois.

### 4. Entrada do lote
- Entrada via API REST (`POST /api/v1/lotes-recebiveis`), payload com os dados do lote e a lista de recebíveis.
- Campos do recebível: `ativo` (texto livre/referência — ver item 7), `valorBruto`, `dataVencimento`, `categoriaRisco`, `moedaPagamento` (opcional — ver item 3; não há campo `moeda`, o ativo é sempre BRL).
- Custo operacional aplicado como spread fixo adicional (valor de referência: 0,5% a.a. → 0,0416% a.m.), configurado na aplicação (não em banco), por ser parâmetro estável.

### 5. Fluxo de aprovação
- **Não há etapa de aprovação** (nem em lote, nem item a item) no MVP. Ao ser recebido, o lote é precificado automaticamente (síncrono, no mesmo caso de uso).
- Status do lote: `RECEBIDO` → `PRECIFICADO` (ou `ERRO` em falha de validação/cálculo).
- Validação estrutural do payload (Bean Validation) é all-or-nothing (400 se inválido). Erros de regra de negócio por recebível (ex.: data de vencimento no passado) rejeitam apenas aquele item — não abortam o lote inteiro; cada recebível carrega seu próprio status (`PRECIFICADO` / `REJEITADO`) e motivo.

### 6. Escopo: liquidação
- Fluxo implementado: **cadastrar lote → precificar (calcular deságio) → liquidar (pagar) recebível a recebível → registrar transação de forma auditável**.
- A liquidação foi adicionada ao MVP (ver item 9) com o seguinte recorte: **um recebível é liquidado de uma vez só, pelo valor presente integral** (`valorPresente`, já na moeda de pagamento) — não há liquidação parcial (pagar uma fração do valor devido de um recebível).
- Aprovação (do lote ou da liquidação), cancelamento e recompra continuam **fora do escopo** deste desafio.

### 7. Cadastro do ativo
- O campo foi renomeado de `cedente` para `ativo` (mais fiel ao que de fato é preenchido nos exemplos — o tipo/instrumento do recebível, ex.: "Duplicata Mercantil", "Cheque Pré-datado", não o nome da empresa cedente). Continua um campo de referência (texto livre), **sem entidade/cadastro próprio** no MVP.

### 8. Escala decimal de valores monetários
- A seção "Decisões de precisão numérica" (2 casas decimais) e a tabela "Tipos de dados canônicos" (`numeric(19,2)`) estavam em contradição. Decisão: prevalece **2 casas decimais** (`numeric(19,2)`) para todo valor monetário, em todas as camadas — inclusive `valorPresente` e `deságio` calculados. A tabela de tipos canônicos e o exemplo de campo foram corrigidos para `numeric(19,2)` / `"15000.00"`.
- Cálculos intermediários usam `BigDecimal` com `MathContext` de alta precisão (sem arredondar); o arredondamento HALF_EVEN para 2 casas ocorre apenas ao fixar o resultado final (`valorPresente`, `deságio`) antes de persistir/retornar.

### 9. Liquidação de recebível — modelo e idempotência
- **Modelo**: novo status `LIQUIDADO` em `StatusRecebivel`, transição só permitida a partir de `PRECIFICADO` (quem nunca foi precificado ou foi `REJEITADO` não tem `valorPresente` — não há o que pagar). Novo campo `liquidadoEm` (timestamp) fica em `recebivel`, junto com o restante do estado do item — não há tabela/agregado `liquidacao` separado, porque não existe liquidação parcial (item 6): a informação cabe inteira em "este recebível está liquidado, desde quando".
- **Endpoint**: `PUT /api/v1/lotes-recebiveis/{loteId}/recebiveis/{recebivelId}/liquidacao` (não `POST`). Escolha deliberada: PUT é idempotente por definição HTTP ("colocar o sub-recurso liquidação neste estado"), o que já comunica a intenção da API antes mesmo de olhar o corpo da resposta. Não há corpo de requisição (liquidação é sempre integral, na data corrente do servidor).
- **Por que idempotência baseada em estado, e não `Idempotency-Key`**: um cabeçalho de chave de idempotência (padrão Stripe) resolve o caso geral de "requisições com corpo variável que não podem repetir efeito". Aqui não há corpo nem variação possível — a ação é inteiramente descrita pela identidade do recebível ("liquidar este") e o próprio estado do recurso já é a fronteira natural de idempotência: chamar de novo um recebível já `LIQUIDADO` é, por definição, a mesma operação. Adicionar uma chave de idempotência seria complexidade sem ganho aqui.
- **Comportamento na repetição**: uma segunda chamada (retry de rede, duplo clique) para um recebível já `LIQUIDADO` retorna `200 OK` com o mesmo resultado (mesmo `liquidadoEm` da primeira vez), sem lançar exceção, sem alterar o estado e **sem gerar um novo evento de auditoria** — `Recebivel.liquidar()` detecta o estado já-liquidado e retorna sem efeito colateral (ver javadoc do método).
- **Corrida concorrente**: duas requisições verdadeiramente simultâneas para o mesmo recebível poderiam, em tese, ambas ler `status=PRECIFICADO` antes de qualquer uma comitar. Em vez de usar o `@Version` (lock otimista) já padrão nas demais escritas — que resolveria a corrida devolvendo `409 Conflict` para o perdedor, obrigando o cliente a tratar esse caso à parte —, optamos por **lock pessimista** (`SELECT ... FOR UPDATE`, via `@Lock(PESSIMISTIC_WRITE)` no repositório) só na busca que antecede a liquidação: a segunda requisição fica bloqueada até a primeira commitar, e então enxerga o recebível já `LIQUIDADO` e retorna o mesmo `200` idempotente — nenhuma das duas chamadas vê um erro. **Ressalva honesta**: não há teste automatizado de concorrência real (duas threads/conexões simultâneas) provando esse comportamento sob corrida — a garantia foi validada via teste de repetição sequencial (chamar duas vezes seguidas) e via leitura do mecanismo de lock, mas não via um teste de race condition disparado de fato.
- **Resposta HTTP**: `200` (sucesso ou repetição idempotente), `404` (lote ou recebível inexistente, ou recebível não pertence ao lote informado), `422` (recebível não está `PRECIFICADO` nem `LIQUIDADO` — nunca foi precificado ou foi rejeitado).

### 10. Observabilidade: métricas via Micrometer/Prometheus/Grafana
- **Stack**: `spring-boot-starter-actuator` + `micrometer-registry-prometheus`, expondo `/actuator/prometheus`. `docker-compose.yml` ganhou os serviços `prometheus` (scrape do backend a cada 15s) e `grafana` (datasource Prometheus e dashboard já provisionados via arquivo — nada para configurar manualmente na UI).
- **Endpoints do actuator expostos**: só `health`, `prometheus` e `info` (`management.endpoints.web.exposure.include`), não o `*` padrão — endpoints como `/env`/`/beans` vazam detalhe interno (variáveis de ambiente, grafo de beans) sem necessidade para este escopo. Coerente com "não há proteção nas APIs" (seção Segurança): o que é exposto é deliberadamente pouco, não protegido por auth.
- **Métricas de negócio customizadas** (além das automáticas de HTTP/JVM/pool de conexão que o Actuator já traz de graça): contadores `creditengine.lotes.precificados` (tag `status`), `creditengine.recebiveis.processados` (tag `status`) e `creditengine.recebiveis.liquidados` (tag `moeda`), além de distribuições (`DistributionSummary`) `creditengine.valor.precificado`/`creditengine.valor.liquidado` (tag `moeda`) para acompanhar o volume financeiro processado. Registradas em `PrecificarLoteService`/`LiquidarRecebivelService` via uma classe dedicada (`CreditEngineMetrics`), não espalhadas como chamadas soltas ao `MeterRegistry` em cada service — mantém os nomes/tags num único lugar.
- **Por que `MeterRegistry` na camada de aplicação não viola a arquitetura hexagonal "rigorosa"**: `MeterRegistry` é uma API vendor-neutral do Micrometer (não uma anotação do Spring) — mesmo racional já usado para o SLF4J (`Logger`), que os services já importam diretamente. A regra do SPEC é a camada de aplicação ser livre de *framework* (Spring), não livre de qualquer biblioteca de infraestrutura.
- **Idempotência refletida também nas métricas, não só no evento de auditoria**: uma chamada repetida de liquidação (retry/duplo clique) não incrementa `creditengine.recebiveis.liquidados` uma segunda vez — o contador só é incrementado quando `Recebivel.liquidar()` retorna `true` (liquidação efetivamente nova), replicando a mesma garantia do item 9 também no plano de observabilidade. Coberto por teste unitário (`LiquidarRecebivelServiceTest`).
- **BigDecimal → double só para métricas**: os `DistributionSummary` de valor convertem `BigDecimal.doubleValue()` no momento de registrar — aceitável porque é uma aproximação para dashboards/alertas, nunca usada em cálculo ou persistência financeira (que continuam 100% `BigDecimal`, sem exceção, em todo o resto do sistema).
- **Portas expostas**: Prometheus em `9090`, Grafana em `3001` (não `3000`, já ocupada pelo frontend). Sem autenticação no Grafana local (`GF_AUTH_ANONYMOUS_ENABLED=true`, role Admin) — mesma decisão de "sem proteção" já adotada no restante do ambiente de desenvolvimento.

### 11. Cotação de câmbio via serviço externo (mock) + estratégia de resiliência
- **Motivo**: a cotação de câmbio deixou de ser um valor estático de configuração (`credit-engine.cotacao-cambio`, item 3) e passou a ser buscada num serviço HTTP externo — mais realista (fontes reais, ex. PTAX, funcionam assim) e permite testar/demonstrar resiliência de verdade contra uma dependência externa instável.
- **Mock via WireMock**: novo serviço `cotacao-cambio-mock` no `docker-compose.yml` (porta `8089`), configurado por um stub declarativo (`mocks/cotacao-cambio/mappings/cotacao-usd-brl.json`) que sempre responde `5.4321` — a mesma cotação que já era o valor padrão do sistema. Escolhido WireMock (não um serviço próprio) por ser o padrão de mercado para mockar API externa via arquivo, mesmo espírito das configs de Prometheus/Grafana já provisionadas por arquivo neste projeto, e por poder ser **parado/religado via `docker compose stop/start`** para simular indisponibilidade real — impossível de simular fielmente se o mock rodasse embutido no próprio processo do backend.
- **Novo port/adapter**: `CotacaoCambioPort` (aplicação) + `CotacaoCambioHttpAdapter` (`adapter.out.http` — primeiro adapter de saída que não é persistência). O adapter constrói seu próprio `RestClient` com `SimpleClientHttpRequestFactory` (timeouts de conexão/leitura explícitos) dentro do construtor, em vez de injetar o `RestClient.Builder` autoconfigurado pelo Spring Boot — deliberado, para não depender de uma autoconfiguração cujo pacote pode ter mudado nesta versão bleeding-edge do Boot (mesma cautela já registrada no PROGRESS.md para outras autoconfigurações). Classe *plain* (sem anotação Spring), como os demais services de aplicação — os valores de configuração (`@Value`) continuam só na camada de composição (`UseCaseConfig`), mesmo padrão já usado para `custoOperacionalPadrao`.
- **Busca 1x por lote, não por item**: `PrecificarLoteService` verifica antes do loop se **algum** recebível do lote precisa de conversão; se sim, busca a cotação **uma única vez** e reusa o mesmo valor para todos os itens cross-currency daquele lote (evita chamadas redundantes e garante que todo o lote use o mesmo snapshot). Lotes 100% BRL nunca chamam o serviço externo.
- **Escada de resiliência** (toda dentro do adapter — nenhuma falha de rede vira exceção para `PrecificarLoteService`):
  1. Circuito aberto (falhas consecutivas recentes ≥ limite)? Pula a chamada de rede, vai direto para o fallback.
  2. Senão, tenta a chamada HTTP com timeout curto (default: 2s conexão / 3s leitura). Sucesso → atualiza o cache do último valor bom, fecha o circuito, retorna o valor.
  3. Falha (timeout, conexão recusada, HTTP não-2xx, corpo malformado) → conta como falha (abre o circuito ao atingir o limite, default 3 falhas / 30s de janela) e cai no fallback.
  4. Fallback: usa o **último valor bom em cache** (em memória) se existir; senão usa o **valor estático de segurança** (`credit-engine.cotacao-cambio.fallback`, default `5.4321` — o mesmo número que já era o padrão antes desta mudança).
- **Por que circuit breaker feito à mão em vez de Resilience4j**: decisão explícita do usuário — Resilience4j é um risco de compatibilidade com o Spring Boot 4.1 (bleeding-edge), que já causou atrito neste projeto antes (`spring-boot-starter-actuator`, `AutoConfigureMockMvc` mudaram de pacote/módulo). O circuit breaker aqui é poucas linhas (contador de falhas consecutivas + janela de "aberto"), suficiente para o único ponto de I/O externo que existe hoje no sistema.
- **Por que não gravar a origem da cotação no recebível**: decisão explícita do usuário — visibilidade de quando o fallback foi usado fica só em log (WARN) e métrica Micrometer (`creditengine.cotacao.consultas`, tag `origem` = `externa`/`cache`/`fallback_estatico`), sem coluna nova/migration no `recebivel`. Trade-off consciente: mais simples agora, mas o registro do recebível não permite reconstruir depois, só pela persistência, se aquela cotação específica veio da fonte real ou de um fallback (só os logs/métricas históricas mostrariam isso, se retidos).
- **Validado manualmente** (não só por teste automatizado): com a stack rodando via `docker-compose`, criado lote cross-currency com o mock no ar (cotação=`externa`), depois com `docker compose stop cotacao-cambio-mock` (cotação=`cache`, mesmo valor, log de WARN, ~2s de timeout), 3 chamadas consecutivas abrindo o circuito (log "Circuito aberto... 3 falhas consecutivas", 4ª chamada respondendo em ~12ms em vez de ~2s), e finalmente `docker compose start cotacao-cambio-mock` + esperar a janela de 30s confirmando volta para `origem=externa`.

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
- No momento não irei utilizar proteção nas APIs do sistema — raciocínio completo (por que, e como
  isso poderia evoluir sem impacto no domínio) documentado em [`DECISIONS.md`](DECISIONS.md), item 1.

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

- Idioma dos nomes de domínio: português (`ativo`, `desagio`, `lote`); nomes técnicos em inglês (`createdAt`, `id`, `status`).
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
    campos de negócio (ativo, valores monetários) nessas linhas — só identificadores, contagens
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
  do ativo só aparecem na resposta HTTP, nunca em linha de log.
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
- **Design system**: Tailwind CSS v4 + shadcn/ui (tema "Nova", cor base neutral/zinc, claro) — substituiu a abordagem inicial de CSS Modules por decisão do usuário, para um visual mais coeso. Primitivos shadcn (`Button`, `Input`, `Label`, `Select`, `Alert`, `Badge`, `Table`, `Card`) vivem em `src/components/ui/` (código gerado pelo CLI `shadcn`, não editado à mão, para facilitar atualizações futuras via `npx shadcn add`); componentes de `shared/ui` que precisam de lógica própria (ex.: `TextField` com label+erro, `Select` com `Controller` do react-hook-form para o Radix Select) compõem esses primitivos. Evite estilos duplicados espalhados — prefira classes utilitárias inline a CSS próprio.
- **Acessibilidade**: HTML semântico, labels em campos, navegação por teclado e atributos ARIA onde necessário.
- **Qualidade**: ESLint + Prettier configurados; testes unitários de componentes e hooks (foco em comportamento, não em implementação).
- **Configuração**: URL da API e demais variáveis via ambiente em build/runtime, nunca hardcoded.

## Build & Deploy
- **Ambiente**: Docker Desktop 4.8x / Docker Compose v2
