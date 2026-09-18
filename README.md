# SRM Credit Engine

Motor de precificação e registro auditável de lotes de recebíveis (duplicatas, contratos)
adquiridos por um FIDC. Recebe um lote de recebíveis, calcula o deságio de cada item com base no
risco do ativo e na moeda, e registra o resultado de forma auditável.

Decisões de arquitetura, premissas de negócio e convenções: [`SPEC.md`](SPEC.md).
Histórico de implementação e decisões técnicas: [`PROGRESS.md`](PROGRESS.md).

## Stack

- **Backend**: Java 25 + Spring Boot 4.1 + Maven, arquitetura hexagonal, PostgreSQL 18 (Flyway),
  documentado via OpenAPI/Swagger.
- **Frontend**: React 19.3 + TypeScript + Vite, TanStack Query, React Hook Form + Zod.
- **Orquestração**: Docker Compose.

## Estrutura do repositório

```
backend/    # API REST (hexagonal: domain, application, adapter, config)
frontend/   # SPA React
openapi.yaml   # spec exportada da API (usada para gerar os tipos do frontend)
SPEC.md        # arquitetura, premissas de negócio, convenções
PROGRESS.md    # histórico de implementação
```

## Subir o projeto (Docker Compose)

```bash
docker compose up -d --build   # primeira vez ou depois de mudar código
docker compose up -d           # subidas seguintes (sem rebuild)
```

Acompanhar o backend inicializar (as migrations do Flyway rodam nesse momento):

```bash
docker compose logs -f backend
```

Aguarde aparecer `Started CreditEngineApplication` no log.

## Parar o projeto

```bash
docker compose down       # para e remove os containers, mantém os dados do banco (volume)
docker compose down -v    # idem, mas também apaga o volume do banco (reset total)
```

## Acessar

| O quê | URL |
|---|---|
| Frontend | http://localhost:3000 |
| Backend direto | http://localhost:8080/api/v1/lotes-recebiveis |
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI JSON/YAML | http://localhost:8080/v3/api-docs ou `/v3/api-docs.yaml` |

## Banco de dados

Credenciais (definidas em [`docker-compose.yml`](docker-compose.yml)):

- Host: `localhost` (de fora do compose) — porta `5432`
- Database: `credit_engine`
- Usuário: `credit_engine`
- Senha: `credit_engine`

Use esses dados em qualquer cliente externo (DBeaver, TablePlus, psql local, etc.), ou entre
direto no container:

```bash
docker compose exec db psql -U credit_engine -d credit_engine
```

## Outros comandos úteis

```bash
docker compose ps                 # status dos 3 serviços (db, backend, frontend)
docker compose logs -f frontend   # logs do nginx/frontend
docker compose logs -f db         # logs do postgres
docker compose restart backend    # reiniciar só um serviço
```

## Rodando sem Docker Compose (desenvolvimento)

Ambiente sem Maven/Node/JDK 25 instalados localmente? Veja a seção "Decisões técnicas" do
[`PROGRESS.md`](PROGRESS.md) para os comandos equivalentes rodando tudo via Docker.

**Backend** (requer um Postgres acessível — `docker compose up -d db`):

```bash
cd backend
mvn spring-boot:run
```

Variáveis de ambiente aceitas (todas com default para desenvolvimento local):
`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`, `SERVER_PORT`, `CUSTO_OPERACIONAL`,
`CORS_ALLOWED_ORIGINS`.

**Frontend**:

```bash
cd frontend
npm install
npm run dev
```

Configure a URL da API em `frontend/.env` (veja `frontend/.env.example`) se o backend não estiver
em `http://localhost:8080`.

## Testes

```bash
# Backend
cd backend
mvn test

# Frontend
cd frontend
npm test
```
