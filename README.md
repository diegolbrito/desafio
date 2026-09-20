# $$$$$$$$ Credit Engine

[![CI](https://github.com/diegolbrito/desafio/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/diegolbrito/desafio/actions/workflows/ci.yml)

Motor de precificação e registro auditável de lotes de recebíveis (duplicatas, contratos)
adquiridos por um FIDC. Recebe um lote de recebíveis, calcula o deságio de cada item com base no
risco do ativo e na moeda, e registra o resultado de forma auditável.

Decisões de arquitetura, premissas de negócio e convenções: [`SPEC.md`](SPEC.md).
Histórico de implementação e decisões técnicas: [`PROGRESS.md`](PROGRESS.md).
Decisões de mais alto nível (segurança, processo, arquitetura), com contexto e justificativa:
[`DECISIONS.md`](DECISIONS.md).
Architecture Decision Record (contexto, objetivo, direcionamento de arquitetura, dependências,
riscos e requisitos): [`ADR.md`](ADR.md).

## Stack

- **Backend**: Java 25 + Spring Boot 4.1 + Maven, arquitetura hexagonal, PostgreSQL 18 (Flyway),
  documentado via OpenAPI/Swagger.
- **Frontend**: React 19.3 + TypeScript + Vite, TanStack Query, React Hook Form + Zod.
- **Orquestração**: Docker Compose.

## Fluxo de trabalho (GitHub Flow)

Adotei o GitHub Flow por ser o modelo mais adequado ao contexto: um projeto de escopo definido,
com uma única linha de produção e ciclos curtos de entrega. A `main` se mantém sempre estável e
deployável, o trabalho acontece em branches curtas por funcionalidade e a integração passa por
Pull Request, onde o pipeline de build e testes (ver [`.github/workflows/ci.yml`](.github/workflows/ci.yml))
é executado antes do merge. Isso entrega rastreabilidade e qualidade sem o custo de branches de
release e hotfix do Git Flow, que só se justificam quando há múltiplas versões suportadas
simultaneamente.

## Escopo e próximos passos

Este repositório cobre o escopo do desafio — deliberadamente sem alguns itens que uma versão de
produção exigiria (raciocínio completo por trás de cada corte em [`DECISIONS.md`](DECISIONS.md)).
Para produção, os próximos passos incluiriam:

- **OIDC com o IdP corporativo** — autenticação/autorização delegadas a um provedor já existente,
  não reimplementadas na aplicação (ver `DECISIONS.md`, item 1).
- **RBAC por perfil da mesa** — controle de acesso por papel (quem pode precificar, aprovar,
  liquidar), não só autenticação.
- **TLS** — tráfego cifrado ponta a ponta (hoje tudo roda em HTTP puro, ambiente local isolado).
- **Mascaramento de dados do cedente em log** — hoje os logs registram `ativo` e valores em texto
  plano; em produção, dado sensível não pode aparecer assim em log.
- **Trilha de auditoria imutável** — o `transacao_evento` já é append-only a nível de aplicação,
  mas não há garantia a nível de infraestrutura (ex.: hash encadeado) contra alteração
  direta no banco.

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
| Grafana (dashboard "$$$$$$$$ Credit Engine" já provisionado) | http://localhost:3001 |
| Mock da cotação de câmbio (WireMock) | http://localhost:8089/api/v1/cotacoes/USD-BRL |

## Simular indisponibilidade do serviço de cotação de câmbio

O backend busca a cotação de câmbio (usada só quando `moedaPagamento` difere de BRL) num serviço
HTTP externo — em desenvolvimento, o mock `cotacao-cambio-mock` (WireMock), que sempre responde
`5.4321`. Para testar a estratégia de resiliência (ver SPEC.md, item 11):

```bash
docker compose stop cotacao-cambio-mock   # simula o serviço fora do ar
# crie um lote cross-currency (moedaPagamento: "USD") - a precificação continua funcionando,
# usando a última cotação conhecida em cache (ou o valor de fallback estático, se nunca tiver
# obtido nenhuma cotação com sucesso ainda). Acompanhe pelo log (WARN) ou pela métrica:
curl -s http://localhost:8080/actuator/prometheus | grep creditengine_cotacao_consultas_total

docker compose start cotacao-cambio-mock  # volta ao normal
```

Depois de 3 falhas consecutivas, o circuit breaker abre por 30s (ambos configuráveis via
`COTACAO_CAMBIO_CB_LIMITE_FALHAS`/`COTACAO_CAMBIO_CB_JANELA_SEGUNDOS`) e as chamadas seguintes
nem tentam a rede — a resposta fica bem mais rápida enquanto o circuito está aberto.

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
`CORS_ALLOWED_ORIGINS`, `COTACAO_CAMBIO_SERVICE_URL`, `COTACAO_CAMBIO_FALLBACK`,
`COTACAO_CAMBIO_TIMEOUT_CONEXAO_MS`, `COTACAO_CAMBIO_TIMEOUT_LEITURA_MS`,
`COTACAO_CAMBIO_CB_LIMITE_FALHAS`, `COTACAO_CAMBIO_CB_JANELA_SEGUNDOS`.

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
