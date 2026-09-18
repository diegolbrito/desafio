# PROGRESS.md — SRM Credit Engine

Arquivo de retomada: o que já foi feito, o que falta e decisões tomadas durante a implementação.
Decisões de negócio/arquitetura ficam registradas em `SPEC.md` (seção "Premissas adotadas"); aqui
ficam apenas decisões técnicas pontuais tomadas durante a construção.

## Status geral
**Todas as 8 etapas do plano original concluídas.** Backend (hexagonal, Spring Boot 4.1/Java 25) e
frontend (React 19.3/TS) implementados, testados e validados end-to-end via `docker-compose`
completo (db + backend + frontend). Não há próxima etapa planejada; próximos passos ficariam a
critério do usuário (ex.: revisão geral, ajustes de UX, deploy real).

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
