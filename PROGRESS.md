# PROGRESS.md — SRM Credit Engine

Arquivo de retomada: o que já foi feito, o que falta e decisões tomadas durante a implementação.
Decisões de negócio/arquitetura ficam registradas em `SPEC.md` (seção "Premissas adotadas"); aqui
ficam apenas decisões técnicas pontuais tomadas durante a construção.

## Status geral
**Todas as 8 etapas do plano original concluídas**, mais as features pós-MVP de liquidação (SPEC.md
item 9, release v0.3.0) e observabilidade/métricas (SPEC.md item 10, ver final da seção
"Concluído"). Backend (hexagonal, Spring Boot 4.1/Java 25) e frontend (React 19.3/TS)
implementados, testados e validados end-to-end via `docker-compose` completo (db + backend +
frontend + prometheus + grafana). A feature de métricas está implementada e testada (64/64 backend)
mas **ainda não commitada/enviada ao repositório** — aguardando aprovação do usuário antes do
commit/PR/release, mesmo fluxo já usado nas features anteriores.

## Concluído
- [x] SPEC.md revisado; seção "Premissas adotadas" preenchida (fórmula de deságio, categorias de
      risco/spread, taxa base/câmbio, entrada de lote via API, fluxo sem aprovação, escopo sem
      liquidação, cedente sem cadastro próprio, escala decimal de 2 casas).
- [x] Etapa 0 — Scaffolding:
  - Estrutura de pastas do backend (hexagonal: `domain`, `application`, `adapter/in/web`,
    `adapter/out/persistence`, `config`) e placeholder de `frontend/`.
  - `docker-compose.yml` com serviço `db` (Postgres 18).
  - `backend/pom.xml`: Spring Boot 4.1.0 (parent), Java 25, `spring-boot-starter-web`,
    `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, Flyway
    (`flyway-core` + `flyway-database-postgresql`), driver PostgreSQL, Testcontainers
    (JUnit Jupiter + Postgres) para testes de integração.
  - `backend/src/main/resources/application.yml`: datasource via variáveis de ambiente
    (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`), `ddl-auto: validate` (schema só
    via Flyway), Flyway apontando para `classpath:db/migration`.
  - Classe de entrada `CreditEngineApplication` (composição, sem lógica).
- [x] Etapa 1 — Domínio puro (pacote `domain`, zero dependência de framework):
  - Enums `Moeda` (com `baseDias()`: BRL=252, USD=360), `CategoriaRisco` (AA..E), `StatusLote`,
    `StatusRecebivel`.
  - `Recebivel` (entidade): invariantes estruturais na criação, `calcularPrazoDias` (rejeita
    vencimento não posterior à data de referência via `PrazoInvalidoException`),
    `aplicarPrecificacao`/`rejeitar` como transições de estado.
  - `LoteRecebiveis` (agregado raiz): valida lista não vazia, `marcarPrecificado`/`marcarErro`.
  - `CalculadoraDesagio`: implementa a fórmula de desconto composto por valor presente definida
    no SPEC — usa `BigDecimal`/`MathContext.DECIMAL128` para os cálculos intermediários e
    arredonda HALF_EVEN só no resultado final (`valorPresente`, `valorDesagio`); deságio é
    derivado por subtração dos valores já arredondados, garantindo
    `valorPresente + valorDesagio == valorBruto` sempre.
  - Exceções de domínio: `DomainException` (base), `RecebivelInvalidoException`,
    `LoteRecebiveisInvalidoException`, `PrazoInvalidoException`.
  - 19 testes unitários (JUnit5 + AssertJ, sem Spring) cobrindo fórmula (BRL/USD, casos
    verificáveis manualmente, invariante valorPresente+deságio=valorBruto, arredondamento) e
    invariantes/transições de `Recebivel`/`LoteRecebiveis`. `mvn test` → 19/19 verdes.
- [x] Etapa 2 — Aplicação (pacote `application`, depende só de `domain` e das próprias ports):
  - Também no `domain`: `EventoTransacao` (record, fábricas estáticas por tipo de fato auditável)
    e `TipoEventoTransacao`, cobrindo o requisito de "registrar a transação de forma auditável".
  - Port de entrada `PrecificarLoteUseCase` + `ComandoPrecificarLote` (DTO de entrada do caso de
    uso; a data de referência da precificação **não** é informada pelo chamador — é resolvida
    internamente via `Clock`, conforme a Premissa 1 do SPEC: "data de entrada do lote").
  - Ports de saída pequenas e específicas (uma por finalidade, não uma interface genérica de
    repositório): `SalvarLoteRecebiveisPort`, `TaxaBaseRepositoryPort`,
    `CategoriaRiscoRepositoryPort`, `RegistrarEventoTransacaoPort`.
  - `ReferenciaNaoEncontradaException` (pacote `application.exception`): erro sistêmico quando
    taxa base/spread de categoria não está cadastrado — distinto das exceções de domínio, pois
    representa falha de configuração, não violação de regra de negócio.
  - `PrecificarLoteService`: orquestra o caso de uso — monta o agregado, precifica cada recebível
    (rejeitando individualmente quem tiver prazo inválido, sem abortar o lote), marca o lote
    `ERRO` se a referência de taxa/spread estiver ausente, persiste via `SalvarLoteRecebiveisPort`
    e registra os eventos de auditoria (`LOTE_RECEBIDO`, um por recebível processado,
    `LOTE_PRECIFICADO`/`LOTE_ERRO`) via `RegistrarEventoTransacaoPort`. Custo operacional recebido
    como `BigDecimal` no construtor (valor vindo de configuração da aplicação, não do banco — a
    camada de composição na Etapa 4 vai ler isso de `application.yml`).
  - 3 testes unitários (JUnit5 + Mockito, mocks das 4 ports de saída): fluxo feliz (2 itens
    precificados), item com vencimento inválido rejeitado sem abortar o lote, e lote marcado
    `ERRO` quando a taxa base não é encontrada. `mvn test` → 22/22 verdes (19 do domínio + 3 da
    aplicação).
- [x] Etapa 3 — Persistência (pacote `adapter.out.persistence`):
  - 5 migrations Flyway: `categoria_risco` e `taxa_base` (tabelas de referência, com seed dos
    valores da Premissa 2/3 do SPEC), `lote_recebivel`, `recebivel` (FK para `lote_recebivel` e
    para `categoria_risco.codigo`), `transacao_evento` (append-only — sem `updated_at`/`version`/
    `deleted_at`, ao contrário das demais, porque nada nela é atualizado depois do insert).
  - Entidades JPA (`CategoriaRiscoEntity`, `TaxaBaseEntity`, `LoteRecebivelEntity`,
    `RecebivelEntity`, `TransacaoEventoEntity`) reaproveitando os enums do `domain`
    (`@Enumerated(STRING)`) em vez de duplicar um enum próprio da persistência — é uma escolha
    aceitável em hexagonal porque adapters podem depender do domínio (a regra é o núcleo nunca
    depender dos adapters, não o contrário). IDs gerados via `@UuidGenerator` (Hibernate), não via
    o `default gen_random_uuid()` do banco (esse default fica como rede de segurança para inserts
    fora do JPA).
  - Repositories Spring Data (`LoteRecebivelJpaRepository`, `CategoriaRiscoJpaRepository`,
    `TaxaBaseJpaRepository`, `TransacaoEventoJpaRepository`).
  - 4 adapters implementando as ports de saída da Etapa 2: `LoteRecebiveisPersistenceAdapter`
    (mapeia o agregado completo lote+recebíveis, salva em cascata e devolve o mesmo objeto de
    domínio com os ids atribuídos via `atribuirId`), `TaxaBasePersistenceAdapter`,
    `CategoriaRiscoPersistenceAdapter` (lançam `ReferenciaNaoEncontradaException` se a
    moeda/categoria não estiver cadastrada), `TransacaoEventoPersistenceAdapter`.
  - Teste de integração (`PersistenciaIntegrationTest`, Testcontainers + Postgres real, migrations
    rodando de verdade, `@Transactional` para isolar cada teste): valida os seeds, o
    salvar+atribuição de ids, o registro de evento referenciando um lote persistido, e um teste de
    ponta a ponta rodando `PrecificarLoteService` com os 4 adapters reais (sem mocks). `mvn test`
    → 27/27 verdes (22 anteriores + 5 de integração).
  - Transação/atomicidade: `PrecificarLoteService` permanece 100% livre de anotações do Spring
    (conforme a arquitetura hexagonal "rigorosa" pedida no SPEC). O limite transacional
    (`@Transactional`) será colocado no controller REST na Etapa 4 — como ele é quem invoca o caso
    de uso, isso basta para que os múltiplos `save()` (lote + eventos) façam parte da mesma
    transação, sem precisar vazar Spring para dentro da camada de aplicação.
- [x] Etapa 4 — REST (pacote `adapter.in.web`):
  - Casos de uso de consulta que faltavam desde a Etapa 2 (o escopo original só cobria
    "precificar"): `ListarLotesRecebiveisUseCase`/`Service` e `BuscarLoteRecebiveisUseCase`/
    `Service`, com ports de saída próprios (`ListarLotesRecebiveisPort`, `BuscarLoteRecebiveisPort`)
    e projeções somente-leitura em `application.port.out` (`LoteRecebiveisResumo` — sem os
    recebíveis, usada na listagem — e `LoteRecebiveisDetalhe`, com os recebíveis, usada no GET de
    detalhe). Motivo de ter duas projeções: evitar N+1 na listagem paginada (critério de
    desempenho do SPEC) sem abrir mão do detalhe completo na consulta de um único lote.
  - `LoteRecebiveisQueryAdapter` (lado de leitura, separado do adapter de escrita da Etapa 3 —
    cada classe com uma única responsabilidade): listagem via `findAll(Pageable)` simples (sem
    tocar na coleção `recebiveis`, então sem lazy-loading nenhum); detalhe via uma query JPQL
    própria com `JOIN FETCH` (`buscarComRecebiveisPorId`) — join fetch é seguro aqui porque é uma
    única linha, não uma página (join fetch + paginação é o cenário problemático, que foi
    evitado de propósito).
  - DTOs de request (`LoteRecebiveisRequest`, `RecebivelRequest`) com Bean Validation e mensagens
    em português. `dataVencimento` **não** tem `@Future`: essa regra é de negócio (rejeita só o
    item, não o payload inteiro — Premissa 5) e já é aplicada no domínio, não na validação
    estrutural.
  - DTOs de response (`LoteRecebiveisResponse`, `RecebivelResponse`, `LoteRecebiveisResumoResponse`,
    `PaginaResponse<T>`) com fábricas estáticas a partir do domínio (usado logo após o POST, sem
    round-trip ao banco) ou das projeções de leitura (usado nos GETs).
  - `LoteRecebiveisController`: `POST` (sempre 201 mesmo com item rejeitado ou lote `ERRO` — a
    "criação" é o registro auditável em si; o corpo da resposta é que informa o resultado
    detalhado), `GET` paginado (`page`, `size`, `sort=campo,direcao` com whitelist de campos
    ordenáveis: `createdAt`, `dataReferencia`, `status`), `GET /{id}` (404 via
    `RecursoNaoEncontradoException` se não existir).
  - `GlobalExceptionHandler` com `ProblemDetail` (RFC 9457): 400 validação (com `errors:
    [{field,message}]`) e JSON malformado, 400 parâmetro inválido, 404 recurso não encontrado, 422
    regra de negócio (`DomainException`), 409 conflito de versão otimista, 500 genérico (loga a
    exceção real, nunca vaza detalhe interno na resposta).
  - `CorrelationIdFilter`: gera `X-Correlation-Id` quando ausente, devolve no header da resposta e
    propaga via SLF4J MDC durante a requisição (padrão de log em `application.yml` inclui
    `[%X{correlationId}]`).
  - `JacksonConfig` + serializador/deserializador customizados de `BigDecimal`: dinheiro e taxas
    sempre como **string** no JSON (nunca number), conforme a tabela de tipos canônicos do SPEC.
  - `UseCaseConfig` (camada de composição, `config` package): instancia os 3 casos de uso como
    beans Spring, injetando os adapters concretos — os próprios casos de uso continuam sem
    nenhuma anotação do Spring.
  - Limite transacional (`@Transactional`) no método `POST` do controller, não no
    `PrecificarLoteService` — mantém a aplicação livre de anotações de framework (decisão já
    registrada na Etapa 3) enquanto garante que lote + recebíveis + eventos de auditoria sejam
    persistidos atomicamente.
  - Teste de integração `LoteRecebiveisControllerIntegrationTest` (MockMvc + Testcontainers):
    fluxo feliz, rejeição parcial de item sem abortar o lote, 400 (lote vazio, valor negativo),
    404, e um cenário criar→buscar→listar ponta a ponta, incluindo verificação de que os valores
    monetários chegam como string no JSON. `mvn test` → 33/33 verdes.
  - Validado manualmente com a aplicação real rodando (`docker compose up -d db` +
    `mvn spring-boot:run`) e `curl` contra os 4 endpoints (POST feliz, GET detalhe, GET lista,
    404, 400) — todas as respostas conferidas manualmente, formatação RFC 9457 e correlation id
    presentes.
- [x] Etapa 5 — OpenAPI (springdoc):
  - `springdoc-openapi-starter-webmvc-ui:3.1.1` (linha 3.x, compatível com Spring Boot 4.1/Spring
    Framework 7 — a linha 2.x é para Boot 3.x). Expõe `/v3/api-docs` (json/yaml) e
    `/swagger-ui/index.html` sem configuração adicional.
  - `OpenApiConfig`: metadados básicos (título, descrição, versão). `@Tag`/`@Operation` no
    `LoteRecebiveisController` para descrições legíveis no Swagger UI.
  - **Achado importante**: o swagger-core trata `BigDecimal` como tipo primitivo "number" por
    padrão, mesmo a API serializando esses campos como string (customização do `JacksonConfig`,
    Etapa 4). Sem correção, o schema OpenAPI mentiria sobre o formato real, e a geração de tipos
    do frontend na Etapa 6 (`openapi-typescript`) produziria `number` onde deveria ser `string`,
    quebrando a regra do SPEC de que dinheiro/taxa nunca é `number` no TS. Tentei primeiro um
    `ModelConverter` global (mais DRY), mas não funciona para `BigDecimal`: o swagger-core resolve
    tipos "primitive-like" (`BigDecimal`, `BigInteger`) via uma tabela interna (`PrimitiveType`)
    antes da chain de converters rodar, então um converter customizado nunca é chamado para esse
    tipo. A solução correta e documentada é anotar cada campo `BigDecimal` com
    `@Schema(type = "string", example = "...")` nos DTOs de request/response — mais repetitivo,
    mas é o jeito que realmente funciona.
  - Spec exportada manualmente para `openapi.yaml` na raiz do repo (via `curl
    localhost:8080/v3/api-docs.yaml` com a app rodando de verdade). **Não há geração automática no
    build ainda** — para regenerar depois de mudar a API: subir `docker compose up -d db` +
    `mvn spring-boot:run`, depois `curl -o openapi.yaml localhost:8080/v3/api-docs.yaml`. Se
    valer a pena automatizar isso (ex.: via `springdoc-openapi-maven-plugin`), fica para decidir
    mais adiante — não fiz agora para não adicionar complexidade de build sem necessidade imediata
    (a Etapa 6 só precisa do arquivo existir, não que ele seja gerado automaticamente).
  - `mvn test` → 33/33 verdes (sem mudança nos testes, só validação manual do schema exportado).
- [x] Etapa 6 — Frontend scaffolding (`frontend/`):
  - Vite 8 + React 19.3 + TypeScript 5.9 (estrito). `package.json` com scripts `dev`, `build`
    (`tsc --noEmit && vite build`), `lint`, `format`, `test` (Vitest), `generate:api-types`
    (`openapi-typescript` lendo `../openapi.yaml`).
  - ESLint 10 (flat config, `eslint.config.js`) com `typescript-eslint`, `react-hooks`,
    `react-refresh`; Prettier configurado. `npm run lint` limpo (0 warnings).
  - Estrutura por feature conforme SPEC: `src/features/lotes-recebiveis/pages/` (placeholder de
    scaffolding — a feature de verdade é a Etapa 7), `src/shared/api` (`httpClient.ts` — único
    ponto de acesso a rede, nenhum componente chama `fetch` direto — e `schema.d.ts`, gerado, não
    escrito à mão), `src/shared/test` (setup do Vitest), `src/routes` (rotas em um único lugar,
    com lazy loading — `React.lazy` + `Suspense`).
  - TanStack Query (`QueryClientProvider`) e `react-router-dom` (`createBrowserRouter`) plugados
    em `main.tsx`. `VITE_API_BASE_URL` via `.env` (nunca hardcoded — `.env.example` documenta a
    variável).
  - Teste de exemplo (`LotesRecebiveisPage.test.tsx`, Vitest + Testing Library) validando o setup
    de testes end a end.
  - Build validado: bundle inicial 336,90 kB (105,70 kB gzip) — dentro do orçamento de 500 KB do
    SPEC — com a página lazy-loaded em chunk separado (0,26 kB). Dev server (`npm run dev`)
    testado manualmente via `curl`, HMR ativo.
  - **Achados de ambiente (documentados em detalhe na seção de decisões técnicas abaixo)**:
    TypeScript 7 (a versão mais nova, reescrita em Go) ainda não é suportado por `openapi-typescript`
    nem por outras ferramentas do ecossistema — usei TypeScript 5.9.3 (estável) no projeto todo;
    Vitest trava indefinidamente ("Timeout waiting for worker to respond") quando os arquivos
    estão em bind mount cross-OS (Windows→container Linux) — só `npm test` é afetado (lint e build
    funcionam normalmente via bind mount); e `tsc -b` com project references exigia emit e gerava
    `vite.config.js`/`.d.ts` indesejados — resolvido simplificando para um único `tsconfig.json`
    (sem `composite`/`references`) e `tsc --noEmit` no lugar de `tsc -b`.
- [x] Etapa 7 — Feature `lotes-recebiveis` completa no frontend:
  - Design system mínimo em `shared/ui`: `Button`, `TextField`, `Select` (com label + erro
    acessível via `aria-invalid`/`aria-describedby`, `forwardRef` para funcionar com
    `register()` do RHF), `Alert` (`role="alert"`/`role="status"`), `Pagination`. Tokens de tema
    (cores, raio) centralizados em `index.css` como CSS custom properties. Estilização via CSS
    Modules (sem framework de UI — não pedido no SPEC).
  - `constants/textos.ts`: todos os textos de UI centralizados (sem string literal em JSX).
  - `utils/formatters.ts`: formatação monetária/percentual/data só na camada de exibição
    (`Intl.NumberFormat`/`Intl.DateTimeFormat`, sempre locale `pt-BR` mesmo para USD — só o
    símbolo muda, os separadores continuam pt-BR, conforme a convenção do SPEC).
  - `api/lotesRecebiveisApi.ts`: funções tipadas a partir do `schema.d.ts` gerado (`components['schemas'][...]`)
    — único ponto de acesso à rede da feature.
  - Hooks TanStack Query: `useLotesRecebiveis` (lista, paginado, `keepPreviousData`),
    `useLoteRecebiveis` (detalhe), `useCriarLoteRecebiveis` (mutation, invalida a query de lista
    no sucesso).
  - `LoteRecebiveisForm`: React Hook Form + Zod (`loteRecebiveisFormSchema.ts`, espelha a
    validação da API) com `useFieldArray` para múltiplos recebíveis por lote (adicionar/remover
    linhas, mínimo 1). Proteção contra submissão dupla via `disabled={isPending}` no botão.
    Mensagem de sucesso/erro inline após o submit.
  - `LotesRecebiveisListagem` e `LoteRecebiveisDetalhe`: tratam loading/vazio/erro/sucesso
    explicitamente, com `StatusBadge` colorido por status.
  - Rotas: `/` (form + listagem) e `/lotes-recebiveis/:id` (detalhe), com `errorElement`
    (`RotaErro`) para falhas inesperadas em cada rota.
  - 22 testes (Vitest + Testing Library + `@testing-library/user-event`): formatadores (funções
    puras), formulário (adicionar/remover linha, validação, submit com sucesso/erro mockando a
    API via `vi.mock`), listagem (loading/vazio/erro/paginação), detalhe (valores formatados,
    motivo de rejeição, erro), página (smoke test). `npm test` → 22/22 verdes.
  - **CORS descoberto e corrigido**: como o frontend (porta 5173) chama a API (porta 8080)
    diretamente, testei manualmente com `curl -H "Origin: http://localhost:5173"` contra o
    backend real e confirmei que um navegador bloquearia as chamadas (sem
    `Access-Control-Allow-Origin` na resposta). Adicionado `CorsConfig` no backend
    (`credit-engine.cors.allowed-origins`, configurável via `CORS_ALLOWED_ORIGINS`, default
    `http://localhost:5173`) — é política de navegador, não autenticação, então não conflita com
    a decisão do SPEC de não proteger as APIs. Reconfirmado via `curl` que o preflight e a
    resposta real agora incluem os headers CORS corretos.
  - Corrigido também um detalhe pendente da Etapa 5: o endpoint `POST` retornava 201 de verdade
    mas o OpenAPI documentava 200 (springdoc não sabia sem `@ApiResponse` explícito) — corrigido,
    spec e tipos do frontend regenerados.
  - `mvn test` (backend, para confirmar que `CorsConfig` não quebrou nada) → 33/33 verdes.
    Build de produção do frontend validado: bundle inicial ~475 kB raw / ~149 kB gzip (dentro do
    orçamento de 500 KB do SPEC, mas com pouca folga em bytes crus — vale observar em mudanças
    futuras).
  - Validado end-to-end de fato: subi backend real + frontend real (dev server) apontando um
    para o outro via `VITE_API_BASE_URL`, confirmei a variável de ambiente injetada corretamente
    no bundle servido e os headers CORS presentes nas respostas. Não há navegador disponível
    neste ambiente para um teste visual manual — a validação de comportamento de UI ficou por
    conta dos testes automatizados com Testing Library (que simulam DOM/acessibilidade fielmente)
    combinados com essa verificação de integração real via curl.

- [x] Etapa 8 — `docker-compose.yml` completo e validação end-to-end:
  - `backend/Dockerfile` (multi-stage: `maven:3.9.11-eclipse-temurin-25` para build,
    `eclipse-temurin:25-jre` para runtime — imagem final sem Maven/JDK completo).
  - `frontend/Dockerfile` (multi-stage: `node:22` para build, `nginx:1.27-alpine` para servir o
    bundle estático) + `nginx.conf`.
  - **Decisão de arquitetura**: o nginx do frontend faz proxy de `/api/*` para o serviço
    `backend:8080` dentro da rede do compose. Isso faz o navegador enxergar frontend e API na
    **mesma origem** (`http://localhost:3000`), eliminando CORS nesse cenário — `VITE_API_BASE_URL`
    é passada vazia (`""`) como build arg, então o `httpClient` usa caminho relativo
    (`/api/v1/...`). A configuração de CORS da Etapa 7 continua existindo e é usada no
    desenvolvimento local (`npm run dev`, origem `http://localhost:5173`, porta diferente da API).
  - `docker-compose.yml`: `db` (Postgres 18, já existia), `backend` (porta 8080 exposta também
    diretamente, `depends_on: db` com `condition: service_healthy`), `frontend` (porta 3000→80,
    `depends_on: backend`).
  - **Bug real encontrado e corrigido na validação end-to-end**: o header `Location` de um `201`
    voltava como `http://localhost/api/...` (porta 80 implícita, errada) em vez de
    `http://localhost:3000/api/...`, porque o backend não sabia que estava atrás de um proxy
    reverso em outra porta. Corrigido com `server.forward-headers-strategy: framework` no
    backend (ativa o `ForwardedHeaderFilter` do Spring) + `proxy_set_header X-Forwarded-Proto`/
    `X-Forwarded-Port` no `nginx.conf`. Reconfirmado via `curl` que o `Location` passou a vir
    correto.
  - Validação end-to-end real (não mockada): subi a stack completa via `docker compose up -d`,
    aguardei o backend inicializar de verdade, e testei via `curl` contra a porta 3000 (a mesma
    que um navegador acessaria): `GET /` retorna o HTML do frontend, `GET /api/v1/lotes-recebiveis`
    e `POST /api/v1/lotes-recebiveis` funcionam através do proxy do nginx, o lote criado aparece
    na listagem em seguida, e a porta 8080 do backend continua acessível diretamente (inclusive
    o Swagger UI). `mvn test` (backend) → 33/33 verdes após as mudanças desta etapa.

## Decisões técnicas tomadas durante a implementação
- Ambiente local não possui Maven/Node/JDK 25 instalados (apenas JDK 24 e Docker Desktop
  disponíveis). Build e testes do backend são validados via imagem Docker de Maven+JDK 25
  (`docker run` montando `backend/`), conforme a seção "Build & Deploy" do SPEC.md, que já define
  Docker como ambiente oficial. O frontend será validado da mesma forma com imagem Node na
  Etapa 6.
- Nenhum commit foi feito ainda na branch `master`/`main` além do `SPEC.md` inicial; os arquivos de
  scaffolding serão commitados em uma branch de feature (`feature/etapa-0-scaffolding`), seguindo o
  github flow definido no SPEC.md, mediante confirmação do usuário.
- Postgres 18 mudou a convenção de diretório de dados (requer o volume montado em
  `/var/lib/postgresql`, não mais em `/var/lib/postgresql/data`). `docker-compose.yml` ajustado
  para o novo layout; validado com `docker compose up -d db` (healthcheck `healthy`).
- Identidade git configurada localmente neste repo (`git config user.name/email`, escopo local,
  confirmada com o usuário) para permitir commits, já que o ambiente não tinha nenhuma configurada.
- Contradição encontrada no SPEC.md entre "Decisões de precisão numérica" (2 casas decimais) e a
  tabela de "Tipos de dados canônicos" (`numeric(19,4)`). Levada ao usuário, que decidiu manter
  **2 casas decimais** (`numeric(19,2)`) em todas as camadas. SPEC.md corrigido (tabela, exemplo de
  campo, e novo item 8 em "Premissas adotadas" registrando a decisão).
- A fórmula de deságio exigia potência com expoente fracionário (`prazoDias/baseDias`), que
  `BigDecimal` não suporta nativamente (`pow` só aceita expoente inteiro) e que não podia ser feita
  em `double` (SPEC proíbe `double`/`float` para dinheiro e taxas). Adicionada a biblioteca
  `ch.obermuhlner:big-math` (`BigDecimalMath.pow`), que calcula potência fracionária via
  exp/log inteiramente em `BigDecimal` com `MathContext` configurável — sem perda de precisão e
  sem usar `double`. É uma dependência puramente matemática (sem I/O/framework), portanto não
  quebra a regra do domínio não depender de infraestrutura.
  **Superado**: ver decisão abaixo (taxa mensal / prazo em meses inteiros / juros compostos
  mensais) — o expoente passou a ser sempre inteiro, `BigDecimal.pow(int, MathContext)` nativo
  passou a bastar, e a dependência `big-math` foi removida do `pom.xml`.
- Mockito emite warning de deprecação sobre self-attach de agent no JDK 25 durante os testes com
  mock (`PrecificarLoteServiceTest`). Não falha o build; é um aviso já conhecido do Mockito em
  JDKs recentes. Se incomodar futuramente, resolve-se configurando o agent explicitamente no
  `maven-surefire-plugin` (`-javaagent`) — não fiz isso agora por ser só um warning.
- **Spring Boot 4.1 modularizou o autoconfigure em artefatos por tecnologia** (evidente pelos
  pacotes `org.springframework.boot.jdbc.autoconfigure`, `org.springframework.boot.hibernate.autoconfigure`
  vistos nos logs). Diferente de versões anteriores, `flyway-core` sozinho **não** é suficiente
  para o Spring Boot rodar as migrations automaticamente — faltava o artefato
  `org.springframework.boot:spring-boot-flyway` (adicionado ao `pom.xml`). Sem ele, a aplicação
  simplesmente subia sem executar nenhuma migration e sem nenhum log/erro de Flyway, o que só foi
  percebido pelo teste de integração falhando com "missing table" na validação do Hibernate.
- **Mapeamento JPA de colunas `char(n)` (tipo `Moeda`)**: `@Enumerated(EnumType.STRING)` sozinho
  gera `varchar` por padrão. Como a coluna `moeda` é `char(3)` no banco (conforme a tabela de tipos
  canônicos do SPEC), a validação do Hibernate (`ddl-auto=validate`) falhava com "wrong column
  type". Corrigido com `@JdbcTypeCode(SqlTypes.CHAR)` + `columnDefinition = "char(3)"` em
  `RecebivelEntity`/`TaxaBaseEntity` — sem isso, `ddl-auto=validate` nunca teria detectado esse
  descompasso silenciosamente (é exatamente o tipo de erro que essa configuração existe para
  pegar).
- **Testcontainers dentro de um container (Docker-in-Docker) no Docker Desktop for Windows**: como
  não há Maven/JDK 25 locais, os testes de integração rodam com `mvn` dentro de um container
  (mesma abordagem da Etapa 0), montando `/var/run/docker.sock` para o Testcontainers conseguir
  subir o Postgres. Dois ajustes foram necessários para isso funcionar nesse ambiente específico:
  1. `TESTCONTAINERS_RYUK_DISABLED=true` — o container sidecar Ryuk (limpeza automática) não
     consegue ser alcançado de volta pelo container "de fora" nesse cenário de containers irmãos;
     como cada execução já usa `--rm` e um container Postgres efêmero, desabilitar o Ryuk é
     aceitável aqui (não é o comportamento recomendado para CI de longa duração).
  2. `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` — sem isso, o Testcontainers tenta usar o
     nome/IP do próprio container "runner" para montar a JDBC URL do Postgres, mas essa
     combinação de host+porta só é válida na perspectiva da VM do Docker Desktop, não de dentro do
     container irmão, causando "Connection refused". Usar `host.docker.internal` (nome que o
     Docker Desktop expõe de forma universal para containers alcançarem o host) resolve porque a
     porta publicada do Postgres é acessível por ali a partir de qualquer container.
  Esses dois `-e` ficam documentados aqui porque são **específicos deste ambiente de
  desenvolvimento** (Docker Desktop for Windows sem Maven/JDK local) — não são necessários quando
  se roda `mvn test` com Maven/JDK instalados diretamente no host, nem em CI Linux nativo.
- **Spring Boot 4.1 migrou para Jackson 3** (`tools.jackson.*`), que renomeou pacotes e classes em
  relação ao Jackson 2 clássico (`com.fasterxml.jackson.*`): `JsonSerializer`→`ValueSerializer`,
  `JsonDeserializer`→`ValueDeserializer`, `Module`→`JacksonModule`, e `JsonGenerator`/`JsonParser`
  agora vivem em `tools.jackson.core` (não mais `com.fasterxml.jackson.core`). Além disso,
  `spring-boot-starter-web` **não** traz `jackson-databind` transitivamente nesta versão — foi
  preciso adicionar `tools.jackson.core:jackson-databind` explicitamente no `pom.xml`. Os métodos
  de serialização também trocaram `throws IOException` por `throws JacksonException`. Isso afeta
  qualquer customização de serialização (`BigDecimalPlainStringSerializer`/
  `BigDecimalLenientDeserializer` em `config/json/`) e é bom lembrar caso apareçam mais
  customizações de Jackson nas próximas etapas.
- **`AutoConfigureMockMvc` mudou de módulo e pacote** no Boot 4.1: não vem mais em
  `spring-boot-starter-test`; é preciso `spring-boot-starter-webmvc-test` (dependência de teste
  separada) e o import correto é `org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc`
  (não mais `org.springframework.boot.test.autoconfigure.web.servlet`).
- `HttpStatus.UNPROCESSABLE_ENTITY` foi depreciado no Spring 7 em favor de
  `HttpStatus.UNPROCESSABLE_CONTENT` (RFC 9110 renomeou a reason phrase do 422); usado no
  `GlobalExceptionHandler`.
- **TypeScript 7 (reescrita nativa/Go) ainda quebra ferramentas do ecossistema.** Ao tentar usar
  `typescript@^7.0.2` (a versão mais recente disponível), `openapi-typescript` falhava em runtime
  com `TypeError: Cannot read properties of undefined (reading 'createKeywordTypeNode')` — o
  pacote acessa `ts.factory`, que não existe mais (ou mudou de forma) na API pública do TS7. Um
  `npm overrides` isolando uma cópia de `typescript@^5.7` só para `openapi-typescript` não
  resolveu (o npm não criou a cópia aninhada, provavelmente por já haver uma versão direta no
  projeto). A solução foi usar `typescript@^5.9.3` (estável) no projeto inteiro — mais simples e
  garante compatibilidade com toda a cadeia de ferramentas (Vite, Vitest, ESLint, `typescript-eslint`,
  `openapi-typescript`). Vale reavaliar quando o ecossistema acompanhar o TS7.
- **`tsc -b` (project references) exige que o projeto referenciado emita algo** — não é possível
  ter `"noEmit": true` num projeto `composite` que é alvo de `references`. Isso gerava
  `vite.config.js`/`vite.config.d.ts` (compilados a partir de `vite.config.ts`) soltos na raiz do
  `frontend/`, poluindo o diretório. Resolvido eliminando o `tsconfig.node.json` separado e usando
  um único `tsconfig.json` (sem `composite`/`references`) cobrindo `src` e `vite.config.ts`, com
  `tsc --noEmit` no script de build em vez de `tsc -b`.
- **Vitest trava com bind mount cross-OS (Windows→Linux) no Docker Desktop.** `npm test` ficava
  preso por 60s com `Error: [vitest-pool]: Failed to start forks worker ... Timeout waiting for
  worker to respond`, tanto com o pool padrão (`forks`) quanto com `pool: 'threads'` — ou seja, não
  é um problema do mecanismo de pool escolhido, é o bind mount em si. Confirmado copiando os
  arquivos para o filesystem nativo do container antes de rodar (`cp -r /appmnt /native && cd
  /native && npm test`): os testes passam em <1s. `npm run lint` e `npm run build` funcionam
  normalmente via bind mount direto — só a inicialização do worker do Vitest é afetada. Para
  rodar os testes do frontend neste ambiente específico, é preciso o passo extra de copiar os
  arquivos para dentro do container antes (documentado aqui; não é necessário com Node instalado
  nativamente no host, nem em CI Linux nativo, igual ao workaround do Testcontainers na Etapa 3).
- **Mudança de convenção da fórmula de deságio: taxa ao mês, prazo em meses inteiros, juros
  compostos mensais** (pedido do usuário após a implementação inicial baseada em taxa anual +
  expoente fracionário em dias corridos). Alterações:
  - `Recebivel.calcularPrazoDias` (dias corridos) → `calcularPrazoMeses` (meses inteiros), com
    arredondamento para cima em mês incompleto (`ChronoUnit.MONTHS.between` + verificação se a
    data-referência + N meses ainda é anterior ao vencimento) — decisão de premissa não
    especificada pelo negócio, documentada em SPEC.md item 1 como as demais.
  - `Moeda.baseDias()` (BRL=252, USD=360) removido — deixou de fazer sentido sem exponenciação
    fracionária; a distinção entre moedas continua existindo só na taxa base de referência de
    cada uma (tabela `taxa_base`).
  - `CalculadoraDesagio.calcular` perdeu o parâmetro `Moeda` e passou a receber `prazoMeses` (long)
    em vez de `prazoDias`; o fator de desconto usa `BigDecimal.pow(int, MathContext)` (expoente
    sempre inteiro), eliminando a necessidade de `BigDecimalMath.pow` — dependência `big-math`
    removida do `pom.xml`.
  - Taxas de referência (seed de `taxa_base` e `categoria_risco`, antes cotadas a.a.) convertidas
    para o equivalente mensal via `taxaMensal = (1 + taxaAnual)^(1/12) − 1`, preservando o mesmo
    significado de mercado (CDI/SOFR e spreads de risco) sob a nova convenção de capitalização.
    Como as migrations Flyway já haviam rodado neste ambiente, os novos valores foram aplicados
    numa migration nova (`V202609181005__ajustar_taxas_para_juros_compostos_mensais.sql`) com
    `UPDATE`, em vez de editar as migrations de seed já existentes (regra geral do Flyway: nunca
    alterar migration já aplicada, pois quebra o checksum de quem já rodou).
  - Testes (`CalculadoraDesagioTest`, `RecebivelTest`, `PersistenciaIntegrationTest`) atualizados
    para a nova assinatura/valores; `PrecificarLoteServiceTest` não precisou de mudança de
    asserção (usa taxas mockadas diretamente, não depende de base de dias).
- **Câmbio cross-currency (título numa moeda, pagamento em outra)** adicionado a pedido do usuário,
  revertendo a premissa anterior de que câmbio "não é necessário" (SPEC.md item 3). Pontos-chave:
  - `Recebivel` ganha `moedaPagamento` (novo overload de `criar` com 6 args; o de 5 delega para
    ele com `moedaPagamento=moeda`, sem quebrar os call sites existentes) e um campo mutável
    `cotacaoCambio`, preenchido só no momento da precificação (`aplicarPrecificacao`), não na
    criação — é resultado do processamento, não dado de entrada do cliente.
  - Nova classe de domínio `ConversorCambial`: converte `ResultadoDesagio` **ao final** do cálculo
    de deságio (que continua 100% na moeda do título). Convenção assumida: `cotacaoCambio` é
    sempre "BRL por 1 USD", independente de qual moeda é o título — simplificação só válida com
    duas moedas. O deságio convertido é derivado por subtração após arredondamento (mesma técnica
    do item 1), preservando o invariante `valorPresente + deságio == valorBruto` em termos da
    moeda de pagamento; `valorBruto` em si não é convertido/persistido (fica só na moeda do
    título, valor de face contratual).
  - **Decisão revisada em seguida pelo usuário**: a cotação inicialmente foi recebida por
    parâmetro na API, por recebível (`RecebivelRequest.cotacaoCambio`) — o usuário pediu para
    mudar para o mesmo padrão de `custoOperacional`: parâmetro de configuração da aplicação
    (`credit-engine.cotacao-cambio`, env var `COTACAO_CAMBIO`), não mais um campo do request.
    Motivo: assim como o custo operacional, é um valor único de sistema (só existem duas moedas,
    logo um único par a converter), não um dado por recebível que o cliente da API deveria
    informar. Isso simplificou a validação (não há mais regra cruzada de "cotação obrigatória
    quando moedas diferem" no `Recebivel.criar` — a cotação sempre existe, vem da configuração) e
    moveu a responsabilidade de fornecê-la para o `PrecificarLoteService` (novo parâmetro de
    construtor `cotacaoCambioPadrao`, injetado via `UseCaseConfig` com `@Value`), na mesma linha
    de `custoOperacionalPadrao`.
  - Persistência: nova migration (`V202609181006__adicionar_cambio_recebivel.sql`) adiciona
    `moeda_pagamento` (not null, backfill = `moeda` para linhas existentes) e `cotacao_cambio`
    (nullable) na tabela `recebivel`, com `CHECK` garantindo consistência (`cotacao_cambio` só
    não-nulo quando `moeda_pagamento <> moeda`) — continua útil mesmo com a cotação vindo de
    configuração: é o snapshot de auditoria de qual valor estava vigente quando aquele recebível
    foi precificado (o parâmetro de aplicação pode mudar entre um deploy e outro).
  - Fios propagados em toda a cadeia: `RecebivelRequest` (só `moedaPagamento`)/`RecebivelResponse`
    (moedaPagamento + cotacaoCambio, ambos ainda expostos na leitura), `ComandoPrecificarLote.
    ComandoRecebivel`, `RecebivelEntity`, `RecebivelLeitura`. Testes: `ConversorCambialTest`,
    cenário cross-currency em `PrecificarLoteServiceTest` (agora configurando
    `cotacaoCambioPadrao` no construtor do service em vez de no comando) e round-trip de
    persistência em `PersistenciaIntegrationTest`.
- **Bug real encontrado e corrigido: `deságio` estava sendo convertido junto com `valorPresente`
  no cross-currency, quando só o `valorPresente` deveria ser.** O usuário reportou "o câmbio não
  está sendo usado quando a moeda é USD"; testes manuais mostraram que a conversão de fato
  acontecia nos dois sentidos (BRL→USD e USD→BRL) e o valor persistido no banco batia com o
  calculado — o sistema não tinha bug de aplicação de câmbio. O problema real só apareceu quando
  o usuário forneceu 3 casos de aferição de negócio (título BRL 100.000,00/3 meses, comparando
  pago em BRL vs. pago em USD a 5,4321): o `deságio` esperado é **idêntico** nos dois casos
  (R$ 7.140,06), só o `valorPresente` muda (R$ 92.859,94 → US$ 17.094,67). Ou seja, `deságio`
  nunca deveria ser convertido — ele representa o custo do desconto no referencial do próprio
  título, não um valor a pagar. Corrigido em `ConversorCambial.converter` (parou de calcular
  `valorBrutoConvertido`/derivar `valorDesagioConvertido` por subtração, agora só converte
  `valorPresente` e devolve o `valorDesagio` original inalterado); `PrecificarLoteService`
  ajustado (não passa mais `valorBruto` para o conversor). **Consequência documentada em
  SPEC.md**: o invariante `valorPresente + deságio == valorBruto` só vale sem cross-currency —
  com conversão, os três ficam em moedas potencialmente diferentes e a soma direta não faz
  sentido. Os 3 casos de aferição viraram teste permanente (`CasosAfericaoTest`), e
  `ConversorCambialTest`/`PrecificarLoteServiceTest` foram ajustados para a nova regra.
  **Causa do "bug fantasma" nos testes manuais anteriores**: o container Docker Compose não
  tinha sido recriado após um `docker compose up -d --build` (o Compose não recria um container
  já "up" só porque a imagem mudou, sem `--force-recreate` ou remoção prévia) — os testes
  manuais rodavam contra uma imagem antiga do backend, mascarando por um tempo qual comportamento
  estava realmente em vigor. Lição: após rebuild de imagem, usar `--force-recreate` (ou `down` +
  `up`) para garantir que o container em execução reflete o código atual.
- **Coleção Bruno revisada e completada**: adicionado request "Criar lote (erro de validacao)"
  (faltava um exemplo de 400/RFC 9457 por validação estrutural — só havia sucesso, rejeição por
  regra de negócio e cross-currency). Ao revisar, percebi que "Criar lote" tinha sido editado
  (fora desta sessão) para usar os mesmos dados dos 3 casos de aferição do negócio
  ("Duplicata Mercantil"/"Cheque Pré-datado"), e que os valores batiam certinho — mas só porque
  `categoria_risco.AA`/`categoria_risco.C` e `taxa_base` (ambas moedas) tinham sido ajustados
  **manualmente no banco deste ambiente**, fora de qualquer migration. Criada
  `V202609181007__fixar_taxas_dos_casos_de_afericao.sql` para versionar esse estado (taxa base
  1% a.m. fixa para BRL/USD, `AA=0,015000`, `C=0,025000`), garantindo que os casos de aferição
  continuem batendo depois de um `docker compose down -v`. `SPEC.md` (itens 2 e 3) e
  `PersistenciaIntegrationTest` (asserts de seed) atualizados para os novos valores.
- **Campo `cedente` renomeado para `ativo`** (pedido do usuário: "faz mais sentido"), em toda a
  cadeia — banco (nova migration `V202609181008__renomear_cedente_para_ativo.sql`, `alter table
  ... rename column`, nunca editar a migration original já aplicada), domínio (`Recebivel`),
  persistência (`RecebivelEntity`), DTOs (`RecebivelRequest`/`RecebivelResponse`/
  `RecebivelLeitura`/`ComandoPrecificarLote.ComandoRecebivel`), frontend (schema Zod, formulário,
  tela de detalhe, textos centralizados em `TEXTOS`), testes (backend e frontend) e coleção
  Bruno. `SPEC.md` atualizado (item 7 renomeado para "Cadastro do ativo", com nota explicando a
  motivação: o campo vinha sendo preenchido com o tipo do instrumento — "Duplicata Mercantil",
  "Cheque Pré-datado" — não o nome de uma empresa cedente); mantidas as referências ao conceito
  de negócio "cedente" (a empresa que cede o recebível ao fundo) onde o texto fala do modelo de
  negócio do FIDC em si, não do nome do campo.
  - Aproveitado o rename para regenerar `openapi.yaml` direto do backend rodando
    (`/v3/api-docs.yaml`) em vez de editar manualmente o arquivo estático desatualizado — o que
    também trouxe `moedaPagamento`/`cotacaoCambio` para o spec e para `frontend/src/shared/api/
    schema.d.ts` (via `npm run generate:api-types`), lacuna que vinha da feature de câmbio e
    ainda não tinha sido fechada.
  - **Workaround do Vitest+bind-mount (Etapa 6) piorou**: com `frontend/node_modules` já instalado
    localmente (239 MB), o `cp -r /appmnt /native` (copiar bind mount inteiro para o filesystem
    nativo do container antes de testar) ficou tão lento que parecia travado (processo em estado
    `D`/uninterruptible sleep por 6+ minutos copiando arquivos pequenos via bind mount cross-OS).
    Resolvido montando o bind mount como `:ro` e copiando só `src/`, `package.json`,
    `package-lock.json`, `tsconfig.json`, `vite.config.ts`, `index.html` (sem `node_modules`) —
    `npm install` reinstala as dependências dentro do container nativo, o que é rápido. Vale
    lembrar dessa diferença: copiar a pasta toda só é rápido enquanto `node_modules` não existir
    localmente.
- **Ativo passou a ser sempre denominado em BRL** (pedido do usuário): não existe mais campo
  `moeda` na entrada do recebível — quem cria o lote só escolhe `moedaPagamento` (opcional; ausente
  = pago na própria moeda do ativo, BRL). Reverte a flexibilidade original do modelo (ativo podia
  ser BRL ou USD) por uma regra de negócio mais simples: o fundo sempre origina o ativo em reais,
  só a moeda de pagamento varia.
  - `Recebivel.criar` perdeu o parâmetro `moeda`; `Moeda.BRL` é atribuída internamente, sempre, no
    construtor privado — deixou de ser um dado de entrada.
  - `ConversorCambial` simplificado: como o ativo é sempre BRL, a única conversão possível é
    BRL → moeda de pagamento (nunca o contrário) — removido o parâmetro `moedaTitulo` e a lógica
    de decidir a direção (`converterValor` não precisa mais de ternário).
  - `RecebivelRequest`/`ComandoPrecificarLote.ComandoRecebivel` perderam o campo `moeda`.
    `RecebivelResponse`/`RecebivelEntity`/`RecebivelLeitura` mantiveram o campo (sempre retorna
    `"BRL"`) — é informação útil na leitura, só deixou de ser aceito na escrita.
  - **Sem migration de schema**: a coluna `moeda` no banco continua exatamente como estava (not
    null, char(3)) — só a aplicação parou de gravar qualquer valor além de `BRL` nela. Linhas
    antigas com `moeda = 'USD'` (de testes anteriores a esta decisão) não foram reescritas —
    ver SPEC.md item 3 para a justificativa (não reescrever histórico de auditoria).
  - Teste ajustado: `marcaLoteComErroQuandoTaxaBaseNaoEncontrada` usava `Moeda.USD` como moeda do
    recebível para simular referência ausente; como isso não é mais possível via API, o cenário
    passou a mockar a ausência da própria taxa base de BRL.
  - Frontend: campo "Moeda" do formulário virou "Moeda de pagamento" (`moedaPagamento` no schema
    Zod), com subtítulo explicando a regra. Label de "Valor bruto" ganhou "(R$)" para reforçar
    que é sempre em reais. Tela de detalhe corrigida para formatar `valorPresente` na
    `moedaPagamento` (não mais na `moeda` do ativo) — esse era, na prática, um bug de exibição
    pré-existente da feature de câmbio (nunca tinha sido corrigido). `openapi.yaml`/`schema.d.ts`
    regenerados do backend real de novo.
- **Migração do frontend de CSS Modules para Tailwind CSS v4 + shadcn/ui** (pedido do usuário,
  "dar uma melhorada no visual"). Confirmado escopo (migração completa, não incremental) e tema
  (neutral/zinc, claro) antes de começar. Pontos-chave:
  - Instalado via CLI oficial (`npx shadcn@latest init --template vite --base radix --preset nova`)
    rodando num container Node com o código-fonte copiado (sem `node_modules` do host, que tornaria
    o `cp` pro filesystem nativo do container extremamente lento — mesma lição do workaround do
    Vitest). Depois `npx shadcn@latest add input label select alert badge table card`.
  - Primitivos gerados ficam em `src/components/ui/` (não editados à mão) — `Button`, `Input`,
    `Label`, `Select` (Radix), `Alert`, `Badge`, `Table`, `Card`. Path alias `@/*` já existia no
    `tsconfig.json` de uma etapa anterior; só faltava espelhar no `vite.config.ts` (`resolve.alias`)
    e adicionar o plugin `@tailwindcss/vite`.
  - `shared/ui/Button.tsx` removido (era só wrapper de 3 variantes; o `Button` do shadcn já cobre
    isso nativamente). `shared/ui/TextField.tsx` e `Alert.tsx` viraram wrappers finos compondo os
    primitivos, preservando a API pública (`label`/`error`, `variant`) para não precisar tocar nos
    componentes de feature que os usam. `shared/ui/Select.tsx` mudou de API: o `<select>` nativo
    (compatível com `register()` do react-hook-form) virou Radix Select, que não é um elemento de
    formulário nativo — precisou de `Controller` (`control`/`name`/`options` como props, em vez de
    spread de `register()`). Todos os `.module.css` removidos.
  - `StatusBadge`, `LotesRecebiveisListagem` e `LoteRecebiveisDetalhe` migrados para `Badge`/`Table`
    do shadcn. `LoteRecebiveisForm` usa `Card` para cada recebível do lote.
  - Build quebrou duas vezes por causas triviais: faltava `@types/node` (o `vite.config.ts` passou
    a importar `node:path`/`import.meta.dirname` para o alias, e o projeto nunca tinha precisado de
    tipos do Node antes) e o `eslint.config.js` não tinha sido copiado para o container de teste
    nativo (erro meu, não da migração em si).
  - Validado com `npm test` (23/23, sem nenhum ajuste nos testes — a interação via Testing
    Library com o Radix Select continuou funcionando), `npm run build` (tsc + vite, sem
    erros de tipo) e verificação visual real: subiu o stack via `docker compose up -d --build
    frontend`, screenshot via Edge headless nativo do Windows (`msedge.exe --headless=new
    --screenshot=...` — `chromium-cli` não estava disponível neste ambiente) confirmando o layout
    novo (Cards, Table, Badge verde/vermelho, Select com chevron) tanto na listagem quanto no
    detalhe, incluindo o cenário cross-currency (valor presente em USD, deságio em BRL).
  - `SPEC.md` ("Arquitetura Frontend e decisões" > "Design system") atualizado para refletir a
    stack real.
- **Feature nova: liquidação de recebível, com idempotência obrigatória** (pedido do usuário —
  reverte parte do item 6 do SPEC.md, "liquidação fora do MVP"). Requisito explícito: a mesma
  requisição repetida (retry de rede, duplo clique) não pode gerar duas liquidações. Decisões
  completas documentadas em `SPEC.md`, novo item 9 — resumo técnico aqui:
  - Domínio: novo `StatusRecebivel.LIQUIDADO` e campo `liquidadoEm` em `Recebivel`. Novo método
    `Recebivel.liquidar(OffsetDateTime)`: se já `LIQUIDADO`, retorna `false` sem lançar exceção nem
    alterar estado (idempotente); se não `PRECIFICADO`, lança `LiquidacaoInvalidaException` (nova,
    estende `DomainException` → 422); caso contrário transiciona e retorna `true`. Novo
    `Recebivel.reconstituir(...)` (factory de rehidratação a partir do estado persistido, distinta
    de `criar()` que sempre valida e começa `PENDENTE`) — necessário porque, ao contrário das
    demais features, esta precisa reidratar um agregado já existente para aplicar uma transição,
    não só criar um novo.
  - Aplicação: `LiquidarRecebivelUseCase`/`LiquidarRecebivelPort` (buscar-para-liquidar + salvar) +
    `LiquidarRecebivelService`, seguindo exatamente o padrão dos demais casos de uso. Só registra
    `EventoTransacao.recebivelLiquidado` (novo `TipoEventoTransacao.RECEBIVEL_LIQUIDADO`) quando a
    liquidação foi efetivamente realizada agora — uma chamada idempotente repetida não duplica o
    evento de auditoria.
  - Persistência: `RecebivelJpaRepository` novo (não existia repositório JPA dedicado a
    `RecebivelEntity` — só era acessado em cascata via `LoteRecebivelJpaRepository`), com
    `buscarParaLiquidar` usando `@Lock(PESSIMISTIC_WRITE)` (`SELECT ... FOR UPDATE`). Nova
    migration `V202609181009__adicionar_liquidacao_recebivel.sql`: coluna `liquidado_em`, e
    `ck_recebivel_status`/`ck_transacao_evento_tipo` recriados (drop+add) incluindo os novos
    valores — **erro cometido e corrigido durante a implementação**: esqueci de atualizar o
    segundo constraint na primeira versão da migration, o que só apareceu como `500` (não `422`)
    no teste de integração de repetição idempotente (`ConstraintViolationException` ao tentar
    inserir `RECEBIVEL_LIQUIDADO` em `transacao_evento`) — a suíte de testes pegou o problema antes
    de qualquer commit, exatamente como o gate pedido pelo usuário deveria funcionar.
  - Web: endpoint `PUT /api/v1/lotes-recebiveis/{loteId}/recebiveis/{recebivelId}/liquidacao` (PUT,
    não POST — ver SPEC.md item 9 para o raciocínio completo sobre por que isso substitui a
    necessidade de um cabeçalho `Idempotency-Key`). Resposta 200 tanto na liquidação real quanto na
    repetição idempotente; 404 se lote/recebível não existir ou não corresponderem entre si; 422 se
    o recebível não estiver `PRECIFICADO` (nunca precificado ou rejeitado).
  - Testes novos: `RecebivelTest` (liquidar feliz, idempotente — não sobrescreve `liquidadoEm` numa
    segunda chamada —, rejeita PENDENTE/REJEITADO), `LiquidarRecebivelServiceTest` (Mockito: não
    encontrado, liquidação registra evento, repetição não registra novo evento),
    `PersistenciaIntegrationTest` (busca com lock + salvar via adapter real), e em
    `LoteRecebiveisControllerIntegrationTest`: fluxo feliz, **duas chamadas HTTP sequenciais
    confirmando o mesmo `liquidadoEm`** (é o teste que prova o requisito central do pedido), 422
    para nunca-precificado, 404 para recebível inexistente e para recebível de outro lote.
    `mvn test` → 60/60 verdes (eram 45 antes desta feature).
  - **Ressalva documentada** (mesma honestidade já usada para o `@Transactional` do lote): não há
    teste automatizado de duas requisições *de fato* concorrentes (threads/conexões simultâneas)
    provando o comportamento do lock pessimista sob corrida real — só o teste sequencial (chamar
    duas vezes seguidas) e a leitura do mecanismo. Registrado em SPEC.md item 9.
  - Frontend: `liquidarRecebivel` (PUT) em `lotesRecebiveisApi.ts`, hook `useLiquidarRecebivel`
    (invalida a query de detalhe no sucesso), botão "Liquidar" em `LoteRecebiveisDetalhe` (só
    aparece para itens `PRECIFICADO`, desabilitado durante a mutação), nova coluna "Liquidado em"
    (`formatarDataHora`, já existente), novo status `LIQUIDADO` no `StatusBadge` (azul, distinto do
    verde de `PRECIFICADO`) e em `TEXTOS`. `openapi.yaml`/`schema.d.ts` regenerados do backend real
    (endpoint novo, `status` com `LIQUIDADO`, campo `liquidadoEm`). Testes novos em
    `LoteRecebiveisDetalhe.test.tsx`: botão aciona a mutation com os ids corretos, não aparece para
    itens não-`PRECIFICADO`, mensagem de erro quando a liquidação falha.
  - Validado manualmente via `curl` contra o backend real (`docker compose up -d --build
    --force-recreate db backend`, volume recriado por causa da migration nova): 3 chamadas
    `PUT` seguidas devolvem o mesmo `liquidadoEm`, 404 para recebível inexistente. Collection Bruno
    ganhou "Liquidar recebivel" (`seq: 7`), com docs explicando como demonstrar a idempotência na
    prática; `npx @usebruno/cli run --env Local -r` → 7/7 passando.
  - Após revisão do usuário: commit na branch `feature/liquidacao-recebivel`, PR #17, merge na
    `main` e release **v0.3.0**.
- **Observabilidade: métricas de negócio + infraestrutura via Micrometer, expostas ao Prometheus,
  visualizadas no Grafana (dashboard já provisionado)** — pedido do usuário. Decisões completas em
  SPEC.md item 10; resumo técnico aqui:
  - Dependências novas: `spring-boot-starter-actuator` + `micrometer-registry-prometheus`.
    `application.yml`: só 3 endpoints do actuator expostos (`health`, `prometheus`, `info` — não o
    `*` padrão, que vazaria `/env`/`/beans`), `management.metrics.distribution.percentiles-histogram.
    http.server.requests=true` (sem isso `http_server_requests_seconds_bucket` não existe, e
    `histogram_quantile` no Grafana não tem o que calcular — descoberto ao validar o dashboard
    manualmente e ver o painel de latência p95 vazio apesar dos outros funcionarem).
  - Nova classe `CreditEngineMetrics` (pacote `application.metrics`) encapsulando o `MeterRegistry`
    do Micrometer com métodos de intenção (`registrarLotePrecificado`, `registrarRecebivelProcessado`,
    `registrarValorPrecificado`, `registrarRecebivelLiquidado`) — nomes/tags de métrica centralizados
    num único lugar, em vez de `registry.counter(...)` espalhado pelos services. Injetada em
    `PrecificarLoteService`/`LiquidarRecebivelService` via `UseCaseConfig` (o `MeterRegistry` já vem
    de graça, autoconfigurado pelo actuator). `MeterRegistry` é API vendor-neutral do Micrometer, não
    anotação do Spring — mesmo racional já usado para o SLF4J direto nos services, não fere a regra
    de aplicação livre de framework.
  - Métricas de negócio: contadores `creditengine.lotes.precificados`/`creditengine.recebiveis.
    processados` (tag `status`), `creditengine.recebiveis.liquidados` (tag `moeda`) e
    `DistributionSummary` `creditengine.valor.precificado`/`creditengine.valor.liquidado` (tag
    `moeda`, valor convertido para `double` só para fins de observabilidade — nunca em cálculo real).
    **Idempotência também no plano de métricas**: `creditengine.recebiveis.liquidados` só incrementa
    quando `Recebivel.liquidar()` retorna `true` (liquidação nova) — uma chamada repetida não infla o
    contador, mesma garantia do evento de auditoria (item 9), coberto por teste.
  - `docker-compose.yml`: serviços novos `prometheus` (scrape do backend a cada 15s, config em
    `observability/prometheus/prometheus.yml`) e `grafana` (porta `3001` — `3000` já é do frontend),
    com datasource e dashboard provisionados via arquivo (`observability/grafana/provisioning/`,
    `observability/grafana/dashboards/credit-engine.json`) — abre pronto em `localhost:3001`, sem
    nenhuma configuração manual na UI. Grafana sem login (`GF_AUTH_ANONYMOUS_ENABLED=true`, role
    Admin), mesma decisão de "sem proteção" já adotada no resto do ambiente de desenvolvimento.
  - Dashboard com 3 seções: **Negócio** (lotes/recebíveis por status, valor total precificado/
    liquidado, recebíveis liquidados), **HTTP** (taxa de requisições por rota e latência **p50, p90
    e p95** por rota, num único painel com três séries por rota) e **Infraestrutura** (memória heap
    JVM, pool de conexões HikariCP).
  - Testes novos: `CreditEngineMetricsTest` (unitário, `SimpleMeterRegistry` real, sem mocks) e
    novas asserções em `PrecificarLoteServiceTest`/`LiquidarRecebivelServiceTest` (incluindo o teste
    que prova que a repetição idempotente não duplica o contador de liquidação). `mvn test` →
    **64/64 verdes** (eram 60).
  - **Lição operacional registrada**: ao rodar a suíte via `docker run ... mvn -q test | tail -300`,
    um erro real de compilação de teste (segunda chamada ao construtor de `PrecificarLoteService`
    não atualizada) passou despercebido porque o exit code capturado era o do `tail` (sempre 0), não
    o do `mvn` — `tail` "engole" o exit code do comando anterior no pipe sem `pipefail`. Corrigido
    rodando com `set -o pipefail` explícito; o erro real (falha de compilação, suite inteira não
    executada) apareceu imediatamente. Vale lembrar disso para qualquer verificação futura de
    exit code através de um pipe com `tail`/`head`/etc á toda vez que o resultado for usado para
    decidir se algo passou ou não.
  - Validado manualmente via `docker compose up -d --build --force-recreate` (backend + prometheus +
    grafana): `/actuator/health` e `/actuator/prometheus` respondendo, Prometheus com o target
    `backend:8080` `"health":"up"`, métricas de negócio aparecendo corretamente após criar/precificar/
    liquidar recebíveis via curl (inclusive confirmando que liquidar 2x seguidas mantém o contador em
    `1.0`), dashboard do Grafana renderizando todos os painéis com dados reais (screenshot via Edge
    headless) depois de gerar tráfego suficiente para os painéis baseados em `rate()`.
  - Após aprovação do usuário (incluindo um ajuste posterior no painel de latência, de p95 sozinho
    para p50+p90+p95 juntos): commit na branch `feature/metricas-micrometer-prometheus-grafana`,
    PR #18, merge na `main` e release **v0.4.0**.
- **Cotação de câmbio migrada de valor estático de configuração para um serviço HTTP externo
  (mock), com estratégia de resiliência** — pedido do usuário, que também pediu explicitamente um
  plano por escrito (via `/plan`) antes de qualquer implementação. Decisões completas em SPEC.md
  item 11; resumo técnico aqui:
  - Novo port `CotacaoCambioPort` (aplicação) + adapter `CotacaoCambioHttpAdapter`
    (`adapter.out.http` — primeiro adapter de saída que não é persistência neste projeto). O
    adapter constrói seu próprio `RestClient` (via `SimpleClientHttpRequestFactory` com timeouts
    explícitos) dentro do construtor, em vez de injetar o `RestClient.Builder` autoconfigurado do
    Spring Boot — decisão deliberada para não depender de uma autoconfiguração que pode ter mudado
    de pacote nesta versão bleeding-edge (mesma cautela já registrada aqui para outras
    autoconfigurações do Boot 4.1). Classe *plain* (sem anotação Spring), com os valores de
    `@Value` só na camada de composição (`UseCaseConfig`) — mesmo padrão de `custoOperacionalPadrao`.
  - `PrecificarLoteService` mudou de receber um `BigDecimal cotacaoCambioPadrao` pronto para
    injetar `CotacaoCambioPort` e buscar a cotação **uma única vez por lote** (não por item, lazy —
    só quando há pelo menos um recebível cross-currency), evitando I/O redundante e garantindo que
    todo o lote use o mesmo snapshot.
  - **Escada de resiliência** dentro do adapter (nenhuma exceção de rede vaza para o service):
    circuit breaker simples (contador de falhas consecutivas + janela de "aberto", default 3
    falhas/30s) → tentativa HTTP com timeout curto (default 2s conexão/3s leitura) → em falha, usa
    o último valor bom em cache (memória, `AtomicReference`) → se nunca teve nenhum, usa o valor
    estático de segurança (`credit-engine.cotacao-cambio.fallback`, default `5.4321` — o mesmo
    número que já era o padrão do sistema antes desta mudança).
  - **Duas decisões de escopo confirmadas com o usuário antes de implementar** (via
    `AskUserQuestion`, durante a fase de plano): circuit breaker feito à mão em vez de Resilience4j
    (risco de compatibilidade com Boot 4.1, que já mordeu este projeto antes com
    `spring-boot-starter-actuator`/`AutoConfigureMockMvc`); e não gravar a origem da cotação
    (externa/cache/fallback) no recebível — só log (WARN) + métrica Micrometer nova
    (`creditengine.cotacao.consultas`, tag `origem`), sem migration/coluna nova.
  - Mock via **WireMock** (`docker-compose.yml`, serviço `cotacao-cambio-mock`, porta `8089`,
    stub declarativo em `mocks/cotacao-cambio/mappings/cotacao-usd-brl.json` sempre retornando
    `5.4321`) — escolhido por ser o padrão de mercado pra mockar API externa via arquivo e por
    poder ser parado/religado via `docker compose stop/start` pra simular indisponibilidade real
    (impossível de simular fielmente com um mock embutido no mesmo processo do backend).
  - Painel novo no dashboard Grafana ("Consultas de cotação cambial, por origem").
  - Testes novos: `CotacaoCambioHttpAdapterTest` (contra um `com.sun.net.httpserver.HttpServer`
    real, não um mock de cliente HTTP — mais fiel para testar timeout/circuit breaker de verdade;
    usa um `Clock` mutável de teste pra avançar a janela do circuito sem esperar 30s de verdade):
    sucesso, fallback estático (nunca teve cache), fallback via cache (após um sucesso anterior),
    circuito abre após o limite de falhas e passa a **não gerar nenhuma requisição HTTP nova**
    (provado contando requisições recebidas pelo servidor de teste), e volta a tentar a rede depois
    que a janela passa. `PrecificarLoteServiceTest` ganhou verificação de que o port nunca é
    chamado para lote 100% BRL, e que é chamado **exatamente uma vez** mesmo com múltiplos itens
    cross-currency no mesmo lote. `mvn test` → **71/71 verdes** (eram 64).
  - **Validado manualmente de ponta a ponta, incluindo o cenário de indisponibilidade real**: subiu
    a stack via `docker compose up`, criou lote cross-currency com o mock no ar
    (`cotacaoCambio` retornado = `5.4321`, métrica `origem=externa`); parou o mock
    (`docker compose stop cotacao-cambio-mock`) e repetiu — mesma cotação, agora via
    `origem=cache`, log de WARN, chamada levou ~2s (timeout de conexão configurado); repetiu mais
    2x seguidas e confirmou pelo log ("Circuito aberto... 3 falhas consecutivas") e pela latência
    (~2s → ~12ms) que o circuito abriu e parou de tentar a rede; religou o mock, esperou a janela
    de 30s passar e confirmou que voltou a `origem=externa`.
  - **Pendente**: aguardando aprovação do usuário antes de commit/PR/release (mesmo fluxo já usado
    nas features anteriores desta sessão).
- **Bug real encontrado e corrigido: nginx do frontend cacheava o IP do backend, quebrando o proxy
  depois de recriar só o container do backend.** O usuário reportou "Não foi possível processar a
  solicitação" no frontend; a causa não era o código da feature de câmbio, e sim o ambiente: o
  `location /api/` do `nginx.conf` usava `proxy_pass http://backend:8080/api/` com hostname
  estático, e o nginx resolve esse tipo de upstream **uma unica vez, na inicializacao do worker**,
  cacheando o IP pelo resto da vida do processo. Como eu tinha recriado o container `backend`
  várias vezes ao longo da sessão (`--force-recreate`, testando a cotação de câmbio) sem nunca
  reiniciar o `frontend`, o Docker realocou o IP antigo do backend para outro container
  (`cotacao-cambio-mock`) — o nginx passou a mandar todo `/api/*` pra lá, que devolvia sua própria
  página de "no stub matched" (404), disfarçada pelo frontend como o erro genérico.
  - Corrigido com o padrão documentado do próprio nginx pra esse problema: `resolver 127.0.0.11
    valid=10s;` (DNS embutido do Docker, sempre nesse endereço dentro de um container) + `proxy_pass`
    usando uma **variável** (`set $backend_upstream http://backend:8080; proxy_pass
    $backend_upstream;`) em vez de um host estático — nginx só re-resolve DNS por trás de
    `proxy_pass` quando o valor vem de uma variável; com host fixo, o cache nunca expira.
  - **Validado reproduzindo o bug de propósito**: subiu um container Alpine descartável ocupando o
    IP antigo do backend (`172.18.0.7`), recriou o `backend` (ficou com `172.18.0.8`, IP realmente
    diferente) **sem tocar no frontend**, esperou o TTL de 10s do resolver, e confirmou que
    `GET /api/v1/lotes-recebiveis` via `localhost:3000` (proxy) voltou a funcionar normalmente —
    sem esse fix, essa mesma sequência reproduz o erro relatado pelo usuário.
  - Escopo do fix: só `frontend/nginx.conf`. Nenhum código de aplicação (backend ou frontend)
    mudou; não afeta `mvn test`/`npm test`.
