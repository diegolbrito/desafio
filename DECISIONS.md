# DECISIONS.md - Credit Engine

Registro de decisões importantes tomadas durante o projeto: arquiteturais, de segurança, de
processo, com o contexto e a justificativa por trás de cada uma. Diferente do [`SPEC.md`](SPEC.md)
("Premissas adotadas", decisões de negócio/domínio) e do [`PROGRESS.md`](PROGRESS.md) (histórico
técnico de implementação), este arquivo reúne decisões de mais alto nível, cujo raciocínio vale a
pena manter explícito mesmo depois que o código já reflete o resultado.

A representação visual da **arquitetura atual** do projeto é o [`C4_MODEL.drawio`](C4_MODEL.drawio)
(formato draw.io, duas páginas: "Nível 1 - Contexto" e "Nível 2 - Containers"). Ele documenta o
sistema como ele é hoje, não o estado proposto no item 2 abaixo.

## 1. Segurança: sem autenticação, autorização ou IdP próprio

Optei conscientemente por não implementar autenticação, autorização ou um IdP no projeto. O
objetivo do desafio é demonstrar o domínio da regra de negócio e da arquitetura do serviço, e a
camada de identidade, em um cenário real, seria delegada a um provedor corporativo já existente,
não reimplementada dentro da aplicação.

Como o ambiente roda isolado, sem exposição externa e sem dados reais, o controle não se
justificaria tecnicamente.

Ainda assim, a API foi mantida stateless e desacoplada, de modo que a validação de token via
OAuth2/OIDC possa ser adicionada como filtro na borda sem impacto na camada de domínio.

## 2. Design de escala para 1 milhão de transações/minuto

> **Status**: Proposta: nada nesta seção está implementado. Diferente do restante deste arquivo
> (decisões já tomadas e refletidas no código) e do [`ADR.md`](ADR.md) (arquitetura **aceita e em
> operação**), o que segue é o **direcionamento proposto** para um patamar de carga que a
> arquitetura atual não foi desenhada para atender.
>
> **Data**: 2026-09-22.

### 2.1 Como ler esta seção

A ordem importa. As subseções 2.2 a 2.4 estabelecem *de onde partimos*, *para onde*, e *o que
quebra primeiro*, sem isso, qualquer escolha de cache/sharding/mensageria vira preferência
pessoal. A subseção 2.5 é o plano em si, dividido em fases que se sobrepõem parcialmente. A
subseção 2.7 amarra tudo num roadmap com **gatilhos métricos** em vez de datas: cada fase só é
justificada quando a anterior atingir um limite medido.

Princípio que guia a seção inteira: **nenhuma mudança estrutural entra sem um número que a
justifique.** O sistema hoje atende bem ao que foi especificado (p95 < 300ms leitura / 500ms
escrita, 1000 requisições concorrentes, [`SPEC.md`](SPEC.md), "Critérios de aceite"). O alvo
proposto é 2 a 3 ordens de grandeza acima disso, e é essa distância que autoriza o custo de cada
item abaixo.

### 2.2 Ponto de partida: a arquitetura de hoje

A arquitetura atual está documentada em [`C4_MODEL.drawio`](C4_MODEL.drawio), níveis 1 (contexto)
e 2 (containers) do modelo C4. O esquema abaixo é uma redução dele aos elementos que importam para
a discussão de escala; para a visão completa (atores, containers e relações), o diagrama é a
referência.

```
Browser ──► nginx (SPA + proxy /api) ──► Spring Boot (1 instância) ──► PostgreSQL 18 (1 nó)
                                               │
                                               └──► Serviço de cotação de câmbio (HTTP)
```

| Característica | Estado atual | Consequência para escala |
|---|---|---|
| Serviços | 1 monolito hexagonal | Bom: unidade de deploy simples, escala horizontal trivial (é stateless) |
| Estado na aplicação | Nenhum (sem sessão) | **Habilitador nº 1** - já pode ser replicado sem mudança |
| Banco | 1 PostgreSQL, sem réplica, sem HA | Ponto único de falha **e** teto único de throughput |
| Processamento | Síncrono ponta a ponta | Latência do pior componente = latência do usuário |
| Cache | Nenhum, exceto cotação em memória por instância | Toda leitura vai ao disco |
| Mensageria | Nenhuma | Escrita de auditoria compete com escrita de negócio |
| Transação | `@Transactional` no controller, cobrindo o caso de uso inteiro | Conexão presa durante I/O externo (ver 2.4.1) |
| Auditoria | `transacao_evento`, append-only, na mesma transação | ~22 inserts por lote de 20 itens - amplificação de escrita de ~2x |
| Chave primária | `uuid` v4 aleatório (`gen_random_uuid()`) | Inserção aleatória no índice B-tree - caro em volume |
| Paginação | `LIMIT/OFFSET` + `count(*)` / `count(*) OVER()` | Degrada linearmente com o tamanho da tabela |
| Migrações | Flyway no boot da aplicação | Contenção de lock com N réplicas subindo juntas |

O que **já está certo** e não deve ser desfeito: a arquitetura hexagonal. Todas as mudanças
propostas aqui - cache, réplica de leitura, fila, sharding - entram como **novas implementações de
*port* de saída**, sem tocar em `domain/`. Isso não é retórica: o `CalculadoraDesagio`, o
`ConversorCambial` e as entidades de domínio devem sair deste plano com zero linhas alteradas.

### 2.3 Traduzindo "1 milhão de transações/minuto" em números

Antes de qualquer decisão, o alvo precisa virar requisições por segundo, linhas por segundo e bytes
por dia. As premissas abaixo são explícitas justamente para poderem ser contestadas e recalculadas.

#### 2.3.1 Premissa de produto

Uma mesa de operações de FIDC tem dezenas de operadores, não um milhão de transações por minuto.
Esse volume só existe num produto diferente: **API B2B multi-tenant** - milhares de cedentes/ERPs
integrados, ou um marketplace de recebíveis. Essa premissa não é cosmética: ela introduz
**`fundo_id`/`tenant_id` no modelo**, que é o candidato natural a chave de shard (Fase 4c). Sem
tenant, o sharding fica com opções bem piores. **Esta é a primeira decisão a confirmar com o
negócio.**

#### 2.3.2 Conversão para RPS

1 milhão de transações/minuto = **16.700 transações/s**. Quanto isso custa em requisições HTTP
depende do que se conta como "transação":

| Leitura de "transação" | Requisições HTTP por transação | RPS resultante |
|---|---|---|
| A - requisição HTTP (leitura literal) | 1 | **16.700** |
| B - operação de negócio na SPA (listagem + detalhe + revalidação) | ~4 | **66.700** |
| C - pico de fechamento da mesa (3x o cenário B) | ~12 | **200.000** |

A leitura B é a que dimensiona o sistema: uma "transação" do ponto de vista do usuário raramente é
uma única chamada. **Alvo de projeto: 70k RPS sustentado, 200k RPS de pico**, com degradação
graciosa (não erro) acima disso. Se o negócio confirmar a leitura literal (A), o alvo cai para
~17k RPS e boa parte das fases finais deixa de se pagar, ver o roadmap em 2.7, cujos gatilhos são
métricos justamente por isso.

#### 2.3.3 Mix de tráfego assumido

| Endpoint | % | RPS @ 70k |
|---|---|---|
| `GET /lotes-recebiveis` (listagem) | 40% | 28.000 |
| `GET /lotes-recebiveis/{id}` (detalhe) | 35% | 24.500 |
| `GET /extrato-liquidacao` | 17% | 11.900 |
| `POST /lotes-recebiveis` (precificação) | 5% | 3.500 |
| `PUT .../liquidacao` | 3% | 2.100 |

≈ **92% leitura / 8% escrita**. Esse desequilíbrio é o que torna cache e réplica de leitura as
alavancas de maior retorno, e na ordem certa: resolver leitura primeiro é mais barato que sharding.

#### 2.3.4 Volume de escrita - o número que decide o sharding

Um `POST` com 20 recebíveis grava hoje, numa única transação:

```
   1 linha   lote_recebivel
+ 20 linhas  recebivel
+ 22 linhas  transacao_evento   (1 LOTE_RECEBIDO + 20 RECEBIVEL_PRECIFICADO + 1 LOTE_PRECIFICADO)
= 43 linhas por requisição
```

A 3.500 `POST`/s → **~150.000 linhas/s**, mais ~2.100 `UPDATE` + 2.100 `INSERT` de liquidação.

| Métrica | Valor estimado |
|---|---|
| Linhas gravadas/s | ~155.000 |
| Volume bruto/dia (com índices) | ~3 TB |
| WAL/dia (amplificação ~2,5x) | ~7–9 TB |
| Linhas em `transacao_evento` após 1 ano | ~2,4 × 10¹² |

Um nó PostgreSQL bem dimensionado sustenta, com folga operacional, algo na faixa de **10–30k
escritas/s**. Precisamos de **5 a 15 nós de escrita**. Sharding deixa de ser opção e vira
requisito, mas só depois que as fases mais baratas tiverem sido esgotadas (ver 2.7).

### 2.4 Ordem de ruptura - o que quebra primeiro

Ordenado por *quando* quebra, não por *quão grave é*. Cada item aponta o arquivo real.

#### 2.4.1 Pool de conexões preso durante chamada HTTP externa - **quebra a ~200 RPS**

`LoteRecebiveisController.criar` é `@Transactional`
([LoteRecebiveisController.java:70](backend/src/main/java/com/srmasset/creditengine/adapter/in/web/LoteRecebiveisController.java#L70)),
e dentro dela `PrecificarLoteService.precificar` chama `cotacaoCambioPort.buscarCotacao()`
([PrecificarLoteService.java:89](backend/src/main/java/com/srmasset/creditengine/application/service/PrecificarLoteService.java#L89)).
Os timeouts configurados são 2s de conexão + 3s de leitura
([application.yml](backend/src/main/resources/application.yml)). Em um cenário de degradação do
provedor de câmbio, **cada requisição cross-currency segura uma conexão do Hikari por até 5
segundos sem executar SQL nenhum**. Com o pool padrão de 10 conexões, bastam ~2 RPS cross-currency
para esgotar o pool e derrubar *todos* os endpoints, inclusive os que nem tocam câmbio.

O circuit breaker mitiga (abre após 3 falhas), mas não cobre a degradação lenta - o caso em que o
serviço responde em 2,9s sem nunca falhar. É o gargalo mais barato de corrigir e o de maior impacto
imediato.

#### 2.4.2 Consulta de referência dentro do laço por item - **quebra a ~500 RPS**

`precificarItem` busca `taxaBaseRepository.buscarTaxaVigente()` e
`categoriaRiscoRepository.buscarSpread()` **por recebível**
([PrecificarLoteService.java:112-113](backend/src/main/java/com/srmasset/creditengine/application/service/PrecificarLoteService.java#L112-L113)).
Um lote de 20 itens faz 40 consultas a duas tabelas que juntas têm **8 linhas** e mudam raramente.
A 3.500 lotes/s isso são 140.000 consultas/s de puro desperdício.

#### 2.4.3 Instância única / Flyway no boot - **quebra a ~1.500 RPS**

Teto de uma JVM. A escala horizontal é trivial (o serviço é stateless), mas hoje `spring.flyway`
roda no startup de cada instância: subir 100 réplicas simultaneamente gera 100 tentativas de
aquisição do lock de migração e acopla o deploy da aplicação ao schema.

#### 2.4.4 Conexões ao PostgreSQL - **quebra a ~5.000 RPS**

Sem *pooler* externo, 300 instâncias × 20 conexões = 6.000 conexões diretas. O PostgreSQL usa um
processo por conexão; a partir de algumas centenas o custo de contexto e de *snapshot* domina. Pela
lei de Little, a demanda real é modesta (70k RPS × ~5ms de tempo em banco ≈ **350 conexões ativas**)
- o problema não é demanda, é ausência de multiplexação.

#### 2.4.5 Leitura no primário - **quebra a ~8.000 RPS**

92% do tráfego é leitura e vai para o mesmo nó que recebe as escritas. Sem cache e sem réplica, o
*buffer cache* do primário é disputado entre o `count(*)` de uma listagem e o `INSERT` de um lote.

#### 2.4.6 Paginação por offset e `count(*)` - **degrada continuamente, colapsa por volume**

- `LoteRecebiveisQueryAdapter.listar` usa `Page` do Spring Data → `count(*)` na tabela inteira a
  cada requisição de listagem.
- `ExtratoLiquidacaoJdbcAdapter` usa `count(*) OVER()` + `LIMIT/OFFSET`
  ([ExtratoLiquidacaoJdbcAdapter.java:35-49](backend/src/main/java/com/srmasset/creditengine/adapter/out/persistence/ExtratoLiquidacaoJdbcAdapter.java#L35-L49)).
  `OFFSET 100000` faz o PostgreSQL ler e descartar 100.000 linhas.

Com 10⁹ linhas, cada listagem vira um *sequential scan*. Isso já estava bem resolvido para o volume
atual - é a **escala** que invalida a escolha, não a escolha em si.

#### 2.4.7 Escrita no nó único - **quebra a ~20.000 escritas/s**

Ver 2.3.4. É aqui que o sharding entra.

#### 2.4.8 Efeitos colaterais da replicação

Não são "rupturas", mas emergem assim que existem N instâncias:

- **Circuit breaker e cache de cotação são por instância**
  ([CotacaoCambioHttpAdapter.java:65-67](backend/src/main/java/com/srmasset/creditengine/adapter/out/http/CotacaoCambioHttpAdapter.java#L65-L67)):
  com 200 instâncias são 200 circuitos independentes e até 200× a carga no provedor externo. Pior:
  **dois lotes precificados no mesmo segundo podem usar cotações diferentes**, por estarem em
  instâncias diferentes. Isso é um problema de *correção*, não de performance.
- **UUID v4 aleatório como PK**: cada `INSERT` cai num ponto aleatório do B-tree → *page splits*,
  amplificação de WAL e péssima localidade de cache. O PostgreSQL 18 (já em uso) oferece
  `uuidv7()`, ordenado no tempo.
- **Cardinalidade de métricas**: `percentiles-histogram: http.server.requests` × N instâncias × N
  URIs gera explosão de séries temporais no Prometheus.

### 2.5 O plano, fase a fase

#### Fase 0 - Medir antes de escalar

Sem baseline, todo o resto é chute. Entregáveis:

1. **Suíte de carga versionada** (k6 ou Gatling) reproduzindo o mix de 2.3.3, rodando contra um
   ambiente com dados realistas (≥ 10⁸ linhas em `recebivel`) - não contra banco vazio.
2. **SLOs formais por endpoint**, derivados de [`SPEC.md`](SPEC.md) e com *error budget*: p95 de
   leitura < 300ms, p95 de escrita < 500ms, disponibilidade 99,9%.
3. **Tracing distribuído** (OpenTelemetry) - hoje há correlation id via MDC, o que resolve
   correlação de log mas não dá *span* com duração por camada. Sem isso, atribuir latência a
   "banco vs. câmbio vs. aplicação" em produção é adivinhação.
4. **Curva de capacidade**: RPS × p95 × utilização, para saber onde está o joelho.

**Critério de saída**: os números de 2.4 confirmados ou corrigidos com medição real.

#### Fase 1 - O que dá para ganhar sem mudar a arquitetura

Correções de custo baixo e retorno alto, na ordem em que quebram.

**1.1 Tirar a chamada externa de dentro da transação.** Resolver a cotação **antes** de abrir a
transação: o caso de uso recebe a cotação já resolvida, ou o limite transacional desce do controller
para um *wrapper* que envolve só a persistência. Elimina 2.4.1 e sozinho deve multiplicar por ~5 a
capacidade de escrita.

**1.2 Cache local de dados de referência.** `Caffeine` como implementação alternativa de
`TaxaBaseRepositoryPort` e `CategoriaRiscoRepositoryPort` (TTL de 30–60s, *refresh* assíncrono).
Elimina 2.4.2. Como são *ports*, entra como adapter novo e o domínio não sabe que mudou nada.

**1.3 Virtual threads.** `spring.threads.virtual.enabled: true`. Java 25 + Boot 4.1 já suportam; o
modelo é thread-por-requisição com trabalho dominado por I/O - exatamente o caso de uso alvo.

**1.4 Tuning de pool e JVM.** Hikari dimensionado por medição (`maximumPoolSize` ≈ núcleos × 2 +
disco efetivo, não "quanto maior melhor"); `-XX:MaxRAMPercentage` e coletor escolhidos por medição
no [Dockerfile](backend/Dockerfile), que hoje sobe com defaults.

**1.5 Separar migração do boot.** `flyway.enabled=false` na aplicação; migração vira *step* do
pipeline (job dedicado), com disciplina de **expand/contract** - toda migração compatível com a
versão anterior do código, pré-requisito para *rolling deploy* sem janela.

**Ganho estimado da fase**: de ~200 RPS de escrita para ~2.000 RPS, sem mudar um componente de
infraestrutura.

#### Fase 2 - Escala horizontal e borda

```
CDN (SPA estática) ─┐
                    ├─► API Gateway / ALB ─► N × credit-engine (stateless, autoscaling)
Clientes B2B ───────┘        │
                             └─ rate limit por tenant, TLS, OIDC
```

- **SPA sai do nginx para CDN.** Hoje o nginx do frontend também faz proxy de `/api`
  ([nginx.conf](frontend/nginx.conf)) - acoplamento que não sobrevive a escala. Assets vão para CDN;
  a API ganha *hostname* próprio atrás de gateway.
- **Autoscaling por RPS e p95**, não por CPU. Carga dominada por I/O tem CPU baixa exatamente
  quando está saturada de latência.
- **PgBouncer em modo `transaction`** entre aplicação e banco - resolve 2.4.4 multiplexando N
  conexões de aplicação em poucas dezenas de conexões reais. Restrição a respeitar: modo
  `transaction` é incompatível com *prepared statements* nomeados de sessão; exige
  `prepareThreshold=0` no JDBC ou PgBouncer ≥ 1.21 com suporte a *prepared statements*.
- **Autenticação na borda**: OIDC no gateway, como já previsto no item 1 deste arquivo. Em escala
  isso deixa de ser só segurança e vira **infraestrutura de cota**: sem identidade não há rate
  limit por tenant, e sem rate limit por tenant um cliente derruba todos.
- **Dimensionamento**: ~2.500 RPS por instância de 4 vCPU → ~30 instâncias no regime de 70k RPS,
  ~90 no pico de 200k, distribuídas em ≥ 3 zonas de disponibilidade.

#### Fase 3 - Cache

Com 92% de leitura, esta é a fase de maior retorno por real investido. Estratégia em dois níveis:

```
Requisição ──► L1 Caffeine (in-process, µs) ──► L2 Redis Cluster (rede, ~1ms) ──► PostgreSQL
```

| Dado | Nível | TTL | Invalidação | Hit ratio esperado |
|---|---|---|---|---|
| `taxa_base`, `categoria_risco` (8 linhas) | L1 | 60s | TTL + *refresh* assíncrono | > 99,9% |
| Cotação de câmbio | L1 alimentado por *push* | - | Pub/sub (ver abaixo) | 100% |
| Detalhe de lote (`GET /{id}`) | L2 | 5 min | Escrita invalida a chave | 80–90% |
| Listagem, página 0 | L2 | 10–30s | TTL curto | 60–70% |
| Extrato (relatório) | L2, chave = hash dos filtros | 60s | TTL | 40–60% |
| Chaves de idempotência | L2 (ver Fase 5) | 24h | - | - |

**Pontos de atenção que definem se o cache ajuda ou atrapalha:**

- **A cotação de câmbio precisa deixar de ser por instância.** Um *poller* dedicado (ou um job no
  próprio serviço com eleição de líder) busca a cotação, publica em Redis e notifica via pub/sub;
  cada instância mantém L1 alimentado por *push*. Resultado: **uma** chamada ao provedor externo por
  janela em vez de N, circuito compartilhado, e o mais importante **todos os lotes do mesmo
  instante precificados com a mesma cotação**. Corrige o problema de correção descrito em 2.4.8.
- **Um lote precificado é imutável até ser liquidado.** É um caso quase ideal de cache: TTL longo,
  invalidação pontual só na liquidação. O detalhe do lote é 35% do tráfego.
- **Proteção contra *stampede***: *single-flight* (uma requisição recarrega, as demais esperam) +
  TTL com jitter. Sem isso, a expiração simultânea de uma chave quente manda 24.500 RPS ao banco de
  uma vez.
- **O que NÃO cachear**: o estado usado na decisão de liquidar. A liquidação lê com
  `SELECT ... FOR UPDATE` e precisa do estado real, cache aí reintroduz exatamente a classe de bug
  descrita no [`POSTMORTEM.md`](POSTMORTEM.md).

**Ganho estimado**: 64k RPS de leitura → ~8k RPS efetivos no banco.

#### Fase 4 - Banco de dados

Três estágios, cada um com gatilho próprio. **Não pular para o sharding.**

##### Fase 4a - Réplicas de leitura + separação de comando/consulta

Um `DataSource` de escrita e um de leitura, escolhidos por *port* - as *ports* de leitura
(`ListarLotesRecebiveisPort`, `BuscarLoteRecebiveisPort`, `BuscarExtratoLiquidacaoPort`) já estão
separadas das de escrita no código atual, o que torna essa mudança quase mecânica. **A separação
hexagonal existente paga dividendo aqui.**

Restrição inescapável: replicação assíncrona significa *lag*. O padrão **"read your own writes"**
(criar um lote e imediatamente consultá-lo) precisa ir ao primário - roteamento explícito por caso
de uso, ou *sticky* ao primário por uma janela curta após escrita do mesmo cliente.

##### Fase 4b - Particionamento e UUIDv7 (antes de sharding)

Particionamento declarativo no PostgreSQL, **por tempo**:

- `transacao_evento` → partição mensal por `ocorrido_em`. É a maior tabela e a mais previsível:
  *append-only*, consultada por período, nunca atualizada.
- `recebivel` e `lote_recebivel` → partição mensal por `created_at`.

Ganhos: `DROP PARTITION` em vez de `DELETE` para retenção, *pruning* de partição nas consultas por
período (o extrato filtra por `liquidado_em`), `VACUUM`/`REINDEX` por partição, índices menores que
cabem em memória.

Junto: **migrar as PKs para `uuidv7()`** (nativo no PostgreSQL 18). Ordenação temporal restaura a
localidade de inserção no B-tree e reduz a amplificação de WAL descrita em 2.4.8.

##### Fase 4c - Sharding

**Gatilho**: escrita sustentada acima de ~15k linhas/s no primário, ou dataset ativo acima do que
cabe em memória no maior nó disponível. Antes disso, o custo operacional não se paga.

**Escolha da chave:**

| Candidata | Prós | Contras | Veredito |
|---|---|---|---|
| `tenant_id` / `fundo_id` | Isolamento natural, transações locais, *noisy neighbor* contido | Exige o campo no modelo (não existe hoje); risco de tenant desbalanceado | **Escolhida** |
| `lote_id` (hash) | Distribuição uniforme; recebíveis acompanham o lote | Perde isolamento por cliente; consultas por tenant viram *scatter-gather* | Fallback |
| `data_referencia` | Trivial de implementar | *Hotspot* garantido: todo o tráfego de hoje num shard | **Rejeitada** |

**Desenho:**

- **1.024 shards lógicos** mapeados para **16 shards físicos** (64 lógicos cada). Resharding vira
  movimentação de shards lógicos, não rehash global. Essa indireção é o que separa um sharding que
  se pode operar de um que não se pode.
- `recebivel`, `transacao_evento` e `lote_recebivel` **coabitam o mesmo shard** - `tenant_id`
  propagado para as três. Isso preserva:
  - a transação atômica lote + recebíveis + eventos, que o [`ADR.md`](ADR.md) identifica
    corretamente como razão de existir do banco único;
  - o `SELECT ... FOR UPDATE` da liquidação, que continua sendo um *lock* local de uma linha.

  **O sharding não introduz transação distribuída em nenhum caminho de escrita.** É a propriedade
  que torna esta proposta viável.
- `taxa_base` e `categoria_risco` são **replicadas em todos os shards** (8 linhas, mudança rara) -
  e, na prática, servidas do cache L1 sem tocar em banco.
- **Roteamento**: ShardingSphere/Citus, ou uma `ShardRouter` própria no adapter de persistência. A
  escolha entre elas muda o adapter, não o domínio.

**O que fica difícil e a resposta para cada um:**

| Problema | Resposta |
|---|---|
| Extrato de liquidação cruza tenants | Não roda no OLTP. Vai para o *read store* analítico (Fase 5) |
| Paginação global ordenada | *Keyset pagination* por shard + merge, ou consulta sempre escopada a tenant |
| `count(*)` global | Contagem aproximada (estatísticas do planner) ou contador mantido em Redis |
| Tenant gigante ("shard quente") | Sub-shard por `lote_id` dentro do tenant, ou shard dedicado |
| Resharding | Movimentação de shards lógicos com dupla escrita + *cutover*, ensaiado em *staging* antes de ser necessário em produção |

##### Correções de consulta (aplicam-se em qualquer estágio)

- **Keyset pagination** substituindo `LIMIT/OFFSET` (`WHERE (liquidado_em, id) < (:cursor)`). Muda o
  contrato da API: cursor opaco em vez de número de página, quebra compatibilidade, precisa ser
  versionado.
- **`count(*)` exato vira opcional**: total aproximado por padrão, exato só sob pedido explícito e
  com limite.
- **`ILIKE '%ativo%'`** no extrato: o índice trigram (`ix_recebivel_ativo_trgm`) segura bem até
  certo volume; acima disso, busca textual vai para índice invertido dedicado (OpenSearch).

#### Fase 5 - Mensageria

Aqui está o maior ganho estrutural de escrita, porque ataca a amplificação de 2,1x descrita em
2.3.4.

##### O que pode ser assíncrono e o que não pode

| Fluxo | Hoje | Proposto | Por quê |
|---|---|---|---|
| Precificar lote (resposta ao cliente) | Síncrono | **Continua síncrono** | O cliente precisa do preço na resposta; é o produto |
| Persistir `transacao_evento` | Síncrono, mesma transação | **Assíncrono via outbox** | 51% das linhas gravadas; não precisa estar no caminho crítico |
| Liquidar recebível | Síncrono com *lock* | **Continua síncrono** | Consistência forte é requisito, ver [`POSTMORTEM.md`](POSTMORTEM.md) |
| Projeções de leitura / extrato | Não existe | **Assíncrono** | Relatório tolera segundos de defasagem |
| Notificação, webhook, exportação | Não existe | **Assíncrono** | Por definição |

**A precificação em si não vira fila.** O [`ADR.md`](ADR.md) já argumenta que o critério de aceite é
definido em latência de requisição-resposta; transformar isso em fila mudaria o produto, não só a
implementação. O que vai para fila é o **efeito colateral**, não a resposta.

Exceção prevista: um endpoint **assíncrono adicional** para lotes grandes (> 500 itens)
`202 Accepted` + polling/webhook, coexistindo com o síncrono para lotes pequenos. Dois contratos,
duas garantias, escolha do cliente.

##### Padrão *outbox* transacional

O problema: gravar no banco **e** publicar em Kafka não é atômico. Publicar direto do código de
negócio gera eventos publicados sem commit (ou commits sem evento), perda de auditoria, que neste
domínio é inaceitável.

```
Transação local:
  INSERT lote_recebivel + recebivel
  INSERT outbox (payload do evento)         ← mesma transação, atômico
COMMIT
        │
        ▼
  CDC (Debezium lendo o WAL) ──► Kafka ──► consumidores
                                             ├─► sink: transacao_evento (particionada)
                                             ├─► projeção de leitura / extrato
                                             └─► analítico (S3/Iceberg ou ClickHouse)
```

Ganho direto: a transação de escrita cai de **43 para 22 linhas** (metade), e as 22 restantes
escrevem numa tabela `outbox` estreita, drenada continuamente. O throughput de escrita do OLTP
praticamente dobra sem adicionar um único shard.

##### Idempotência de ponta a ponta

Em escala, retry não é exceção, é o regime normal (timeout de gateway, retry de cliente,
*rebalance* de consumidor). A idempotência atual da liquidação é **baseada em estado**
(`Recebivel.liquidar()` + *lock* pessimista), o que cobre a repetição da mesma operação, mas não
distingue "retry da mesma intenção" de "segunda intenção legítima". Dado que o
[`POSTMORTEM.md`](POSTMORTEM.md) documenta exatamente um incidente de liquidações duplicadas, vale
reforçar:

- Header **`Idempotency-Key`** obrigatório nas escritas, persistido com *unique constraint* (tabela
  local ao shard, ou Redis com fallback em banco), guardando a resposta original.
- Consumidores Kafka **idempotentes por chave** (`recebivel_id`), partição escolhida por chave para
  garantir ordem por entidade.
- Entrega *at-least-once* assumida em todo consumidor. Ninguém depende de *exactly-once*.

##### Dimensionamento

| Item | Valor |
|---|---|
| Mensagens/s (eventos de auditoria) | ~77.000 |
| Tamanho médio | ~300 B |
| Throughput | ~23 MB/s |
| Partições | 48 (≈ 1.600 msg/s por partição, com folga para paralelismo de consumo) |
| Brokers | 6, replicação 3, `min.insync.replicas=2` |
| Retenção | 7 dias no Kafka; permanente no *sink* colunar |

Carga confortável para Kafka. O dimensionamento é determinado por paralelismo de consumo e
tolerância a falha, não por throughput bruto.

##### Backpressure e DLQ

*Lag* de consumidor monitorado com alerta; DLQ por tópico com *retry* exponencial; e decisão
importante, **falha no pipeline de auditoria não pode derrubar a precificação**. A `outbox` cresce,
o alerta dispara, o negócio continua. O inverso (parar de precificar porque o Kafka caiu) seria
trocar um problema de disponibilidade por outro maior.

#### Fase 6 - Resiliência, isolamento e degradação

Em 70k RPS, falha parcial é o estado normal do sistema. O objetivo deixa de ser "não falhar" e passa
a ser "falhar num pedaço pequeno e previsível".

- **Rate limiting por tenant** no gateway (token bucket), com cota diferenciada por plano.
- ***Bulkheads***: pools separados por classe de operação, para que o extrato (caro) não consuma a
  capacidade da liquidação (crítica).
- ***Load shedding*** com prioridade: sob saturação, rejeitar relatório antes de rejeitar
  liquidação. Requer classificar os endpoints por criticidade, decisão de negócio, não técnica.
- **Circuit breaker compartilhado** e com estado distribuído, substituindo o atual por instância
  ([CotacaoCambioHttpAdapter.java](backend/src/main/java/com/srmasset/creditengine/adapter/out/http/CotacaoCambioHttpAdapter.java)).
  A escada de *fallback* existente (cache → valor estático) está correta e deve ser preservada.
- **Timeout e *retry* com jitter** em toda chamada de rede; *budget* de retry para evitar tempestade.
- **Multi-AZ obrigatório; multi-região** conforme requisito regulatório de RPO/RTO, a definir com o
  negócio, é a decisão mais cara desta seção.
- **Testes de caos** em *staging*: matar shard, matar broker, degradar o provedor de câmbio.

#### Fase 7 - Observabilidade em escala

O que funciona com uma instância não funciona com 200:

- **Cardinalidade**: `percentiles-histogram` por URI × 200 instâncias explode o Prometheus. Migrar
  para agregação por *recording rule* + *remote write* para armazenamento de longo prazo, com
  normalização de rótulos (`uri` templatizada, nunca com id).
- **Tracing amostrado** (1–5%, com amostragem garantida para erros e cauda lenta).
- **Log estruturado em JSON** com amostragem no nível `INFO`, o `CorrelationIdFilter` que loga toda
  requisição gera 70.000 linhas/s; a 200 B/linha são ~1,2 TB/dia só de log de acesso.
- **SLO por tenant**, não só global: um cliente grande degradado desaparece na média agregada.
- **Métricas de shard**: *lag* de replicação, distribuição de carga, detecção de shard quente.

### 2.6 Arquitetura-alvo

```
                        ┌──────────── CDN (SPA) ────────────┐
                        │                                   │
   Clientes B2B / SPA ──┴──► API Gateway (TLS, OIDC, rate limit por tenant)
                                        │
                        ┌───────────────┴───────────────┐
                        ▼                               ▼
              credit-engine (N instâncias)      credit-engine-query
              escrita + leitura quente          relatórios / extrato
                        │                               │
          ┌─────────────┼─────────────┐                 │
          ▼             ▼             ▼                 ▼
    L1 Caffeine   Redis Cluster   PgBouncer        Read store analítico
    (referência)  (L2 + idem-     (multiplexação)  (ClickHouse / Iceberg)
          │        potência)           │                 ▲
          │                            ▼                 │
          │              ┌──── PostgreSQL shards 1..16 ──┤
          │              │     (primário + 2 réplicas,   │
          │              │      partição mensal)         │
          │              │            │                  │
          │              │            ▼ outbox           │
          │              │      Debezium (CDC)           │
          │              │            │                  │
          │              │            ▼                  │
          │              │      Kafka (48 partições) ────┤
          │              │            │                  │
          │              │            └─► sink transacao_evento + projeções
          ▼              │
   Poller de cotação ────┘──► provedor externo de câmbio (1 chamada por janela)
```

Este é o estado **proposto**. O estado atual é o do [`C4_MODEL.drawio`](C4_MODEL.drawio), se
alguma fase deste plano for executada, o diagrama precisa ser atualizado junto, para continuar
valendo como retrato do que está em operação.

**O que não muda**: `domain/` inteiro. `CalculadoraDesagio`, `ConversorCambial`, `Recebivel`,
`LoteRecebiveis` e as exceções de domínio saem deste plano sem alteração. Todo o resto entra como
adapter novo ou infraestrutura. Esse é o retorno concreto do rigor hexagonal adotado no início do
projeto, e o argumento de que a decisão original estava certa.

### 2.7 Roadmap com gatilhos

Fases avançam por **métrica medida**, não por calendário.

| Fase | Entra quando | Entrega | Capacidade após | Esforço |
|---|---|---|---|---|
| **0. Medir** | Sempre primeiro | Carga versionada, SLO, tracing | - | 1–2 semanas |
| **1. Correções locais** | Imediato | Transação sem I/O externo, cache L1, virtual threads, Flyway fora do boot | ~2k RPS | 1–2 semanas |
| **2. Horizontal + borda** | p95 estourando com 1 instância | CDN, gateway, autoscaling, PgBouncer, OIDC | ~10k RPS | 3–4 semanas |
| **3. Cache distribuído** | Leitura > 5k RPS no banco | Redis L2, cotação centralizada, *stampede protection* | ~30k RPS | 3–4 semanas |
| **4a. Réplicas + CQRS** | Leitura saturando o primário | Réplicas, roteamento por *port* | ~50k RPS leitura | 2–3 semanas |
| **4b. Particionamento + UUIDv7** | `transacao_evento` > 10⁹ linhas | Partição mensal, retenção por `DROP` | Escrita +40% | 3–4 semanas |
| **5. Mensageria + outbox** | Escrita > 5k RPS | Outbox, CDC, Kafka, *sink* analítico | Escrita 2x, extrato sai do OLTP | 6–8 semanas |
| **4c. Sharding** | Escrita > 15k linhas/s sustentado | `tenant_id`, 1024 lógicos / 16 físicos, roteamento | **70k RPS / 200k pico** | 10–14 semanas |
| **6–7. Resiliência + observabilidade** | Contínuo, em paralelo | Bulkhead, shedding, SLO por tenant | - | Contínuo |

**Pré-requisito de produto travando a Fase 4c**: a decisão sobre `tenant_id` (2.3.1). Ela precisa
estar tomada bem antes, introduzir chave de shard em tabela com 10⁹ linhas é significativamente
mais caro do que nascer com ela.

### 2.8 O que deliberadamente NÃO fazer

Tão importante quanto a lista do que fazer:

- **Não quebrar em microserviços por padrão.** A justificativa do [`ADR.md`](ADR.md) continua válida
  em 70k RPS: é **um** contexto delimitado com transações atômicas entre entidades relacionadas.
  Escalar ≠ fragmentar. Separar o serviço de *query*/relatório (Fase 5) é a única decomposição que
  este plano propõe, e por um motivo concreto: perfil de recurso oposto.
- **Não trocar PostgreSQL por NoSQL.** O argumento do ADR (precisão decimal, integridade
  referencial, ACID, *lock* pessimista na liquidação) não enfraquece com volume, fortalece.
  Sharding de PostgreSQL preserva todas essas propriedades; migrar para consistência eventual
  reintroduziria na aplicação exatamente o que o banco já garante, e num domínio financeiro.
- **Não tornar a precificação assíncrona por completo.** Muda o produto, não a escala.
- **Não cachear estado de liquidação.** Reintroduz a classe de bug do [`POSTMORTEM.md`](POSTMORTEM.md).
- **Não fazer sharding antes de cache e réplica.** É a mudança mais cara e mais irreversível da
  seção. Custo operacional permanente, não pontual.
- **Não buscar *exactly-once*.** *At-least-once* + idempotência é mais simples, mais barato e mais
  robusto.

### 2.9 Riscos

| Risco | Impacto | Mitigação |
|---|---|---|
| `tenant_id` não existe no modelo | Bloqueia o sharding ou torna-o muito mais caro | Decidir cedo; introduzir o campo já na Fase 1, mesmo sem uso imediato |
| Migração para UUIDv7 em tabela grande | Janela de indisponibilidade | Expand/contract: nova coluna, dupla escrita, backfill em lotes, troca de PK |
| Keyset pagination quebra o contrato da API | Frontend e clientes B2B precisam mudar | Versionar (`/api/v2`), manter offset com limite rígido de profundidade em v1 |
| *Lag* de replicação visível ao usuário | "Criei um lote e ele não aparece" | Roteamento ao primário em read-your-own-writes |
| Custo operacional de 16 shards + Kafka + Redis | Time pequeno não sustenta | Gerenciado (RDS/Aurora, MSK, ElastiCache) antes de autogerido; SRE dedicado |
| Provedor de câmbio vira gargalo compartilhado | Poller único é SPOF | Múltiplos provedores, eleição de líder, escada de fallback já existente preservada |
| Premissa de 1M transações/min não se confirmar | Meses de engenharia desperdiçados | Gatilhos métricos por fase, nenhuma fase começa sem o número que a justifica |

### 2.10 Custo - ordem de grandeza

Estimativa grosseira de infraestrutura no regime de 70k RPS, para comparação relativa entre fases
(não para orçamento):

| Componente | Dimensão | Peso relativo |
|---|---|---|
| Aplicação (30–90 instâncias, 4 vCPU) | Autoscaling | ~20% |
| PostgreSQL (16 shards × 3 nós) | O item dominante | ~45% |
| Kafka (6 brokers) + CDC | | ~10% |
| Redis Cluster (~8 nós) | | ~8% |
| Read store analítico | | ~7% |
| CDN + gateway + egress | | ~5% |
| Observabilidade (métricas, log, trace em 70k RPS) | Frequentemente subestimado | ~5% |

Leitura principal da tabela: **o banco domina o custo**, o que é mais um argumento para esgotar
cache e réplica (Fases 3 e 4a) antes de multiplicar nós de escrita, cada ponto de *hit ratio* no
cache tem retorno desproporcional.

### 2.11 Resumo

1. **1M transações/min ≈ 70k RPS sustentado, 200k de pico** na leitura que dimensiona o sistema
   (2.3.2), 92% leitura, ~155k linhas gravadas/s.
2. **O que quebra primeiro não é o banco** é uma chamada HTTP externa dentro de uma transação
   (~200 RPS). Duas semanas de trabalho multiplicam a capacidade por 10, sem mexer em
   infraestrutura.
3. **Cache é a maior alavanca**, dada a proporção de leitura: 64k RPS de leitura → ~8k no banco.
4. **Mensageria com padrão outbox dobra a capacidade de escrita** ao tirar 51% das linhas
   (auditoria) do caminho crítico antes e independentemente de sharding.
5. **Sharding por `tenant_id`** é necessário no alvo final, mas é a última fase e depende de uma
   decisão de produto que precisa ser tomada logo.
6. **A arquitetura hexagonal atual é um ativo, não um obstáculo.** Cache, réplica, fila e sharding
   entram todos como adapters. O domínio não muda.
