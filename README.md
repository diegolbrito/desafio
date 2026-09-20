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
| Métricas (Prometheus format) | http://localhost:8080/actuator/prometheus |
| Health check | http://localhost:8080/actuator/health |
| Prometheus | http://localhost:9090 |
| Grafana (dashboard "SRM Credit Engine" já provisionado) | http://localhost:3001 |

## Chamando a API (Bruno)

A pasta [`bruno/`](bruno/) é uma coleção do [Bruno](https://www.usebruno.com/) pronta pra usar —
abra a pasta direto no Bruno ("Open Collection") e já aparecem os requests (`Criar lote`,
`Criar lote (item rejeitado)`, `Listar lotes`, `Buscar lote por id`) e dois ambientes (`Local`,
porta 8080 direto no backend; `Docker Compose`, porta 3000 via proxy do frontend). Escolha o
ambiente no canto superior direito antes de rodar. Alternativa: importar `openapi.yaml` direto no
Bruno (File > Import > OpenAPI Collection) para gerar os requests automaticamente a partir do spec.

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

## Depurar o backend (debug remoto)

Como o backend roda via Docker (sem Maven/JDK 25 instalados localmente), a forma de debugar é
anexar o VS Code a uma JVM remota com o agente JDWP habilitado.

**Pré-requisito**: extensão [Extension Pack for Java](https://marketplace.visualstudio.com/items?itemName=vscjava.vscode-java-pack)
no VS Code (inclui o debugger e o suporte de linguagem necessários).

Suba o backend com a porta de debug exposta e o agente JDWP ligado:

```powershell
# PowerShell (padrão no Windows)
docker compose up -d db

docker run -d --name credit-engine-app -p 8080:8080 -p 5005:5005 -v "${PWD}/backend:/app" -w /app -v maven-repo-cache:/root/.m2 -e DB_HOST=host.docker.internal maven:3.9.11-eclipse-temurin-25 mvn -B spring-boot:run "-Dspring-boot.run.jvmArguments=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

```bash
# Bash / Git Bash / macOS / Linux
docker compose up -d db

docker run -d --name credit-engine-app -p 8080:8080 -p 5005:5005 \
  -v "$(pwd)/backend:/app" -w /app -v maven-repo-cache:/root/.m2 \
  -e DB_HOST=host.docker.internal \
  maven:3.9.11-eclipse-temurin-25 mvn -B spring-boot:run \
  -Dspring-boot.run.jvmArguments="-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005"
```

> **Atenção**: os dois comandos não são intercambiáveis. O PowerShell não continua o comando na
> linha seguinte com `\` (ele executa cada linha separada, então o container sobe sem o
> `mvn spring-boot:run` de verdade e morre na hora — sintoma: "sobe e encerra em seguida"). Use o
> bloco do shell que você está usando de fato.

Coloque breakpoints no código e rode a configuração **"Attach ao backend (Docker)"** no painel
*Run and Debug* (F5) — já está em [`.vscode/launch.json`](.vscode/launch.json). Como o container
usa bind mount dos mesmos arquivos que você edita, os breakpoints resolvem normalmente contra o
código-fonte.

Use `suspend=y` no lugar de `suspend=n` se quiser que a aplicação espere o debugger conectar antes
de terminar de subir (útil para depurar código que roda no startup).

Pare o container de debug com `docker stop credit-engine-app && docker rm credit-engine-app`.

## Testes

```bash
# Backend
cd backend
mvn test

# Frontend
cd frontend
npm test
```
