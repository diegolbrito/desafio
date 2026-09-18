# PROGRESS.md — SRM Credit Engine

Arquivo de retomada: o que já foi feito, o que falta e decisões tomadas durante a implementação.
Decisões de negócio/arquitetura ficam registradas em `SPEC.md` (seção "Premissas adotadas"); aqui
ficam apenas decisões técnicas pontuais tomadas durante a construção.

## Status geral
Etapa 0 (scaffolding) concluída. Próxima: Etapa 1 (domínio puro).

## Concluído
- [x] SPEC.md revisado; seção "Premissas adotadas" preenchida (fórmula de deságio, categorias de
      risco/spread, taxa base/câmbio, entrada de lote via API, fluxo sem aprovação, escopo sem
      liquidação, cedente sem cadastro próprio).
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

## Pendente (próximas etapas)
- [ ] Etapa 1 — Domínio puro: enums (`Moeda`, `CategoriaRisco`, `StatusLote`, `StatusRecebivel`),
      value objects, `CalculadoraDeságio` (fórmula definida em SPEC.md), exceções de domínio +
      testes unitários (sem Spring).
- [ ] Etapa 2 — Aplicação: ports de entrada/saída, `PrecificarLoteService` + testes com mocks.
- [ ] Etapa 3 — Persistência: migrations Flyway (`lote_recebivel`, `recebivel`, `categoria_risco`
      seed, `taxa_base` seed, `transacao_evento` append-only), entidades JPA, repositories +
      testes de integração (Testcontainers).
- [ ] Etapa 4 — REST: DTOs + Bean Validation, `LoteRecebiveisController`
      (`POST`/`GET /api/v1/lotes-recebiveis`), `GlobalExceptionHandler` (RFC 9457),
      `X-Correlation-Id` + testes de integração.
- [ ] Etapa 5 — OpenAPI exportado (springdoc).
- [ ] Etapa 6 — Frontend scaffolding (Vite + React 19.3 + TS, ESLint/Prettier, tipos gerados do
      OpenAPI, client HTTP + TanStack Query).
- [ ] Etapa 7 — Feature `lotes-recebiveis` no frontend (formulário RHF+Zod, listagem paginada,
      detalhe do lote) + testes de componentes/hooks.
- [ ] Etapa 8 — `docker-compose.yml` completo (db + backend + frontend) + validação end-to-end
      manual.

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
