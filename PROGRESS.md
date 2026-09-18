# PROGRESS.md — SRM Credit Engine

Arquivo de retomada: o que já foi feito, o que falta e decisões tomadas durante a implementação.
Decisões de negócio/arquitetura ficam registradas em `SPEC.md` (seção "Premissas adotadas"); aqui
ficam apenas decisões técnicas pontuais tomadas durante a construção.

## Status geral
Etapa 4 (REST) concluída. Próxima: Etapa 5 (OpenAPI).

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

## Pendente (próximas etapas)
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
- Identidade git configurada localmente neste repo (`git config user.name/email`, escopo local,
  confirmada com o usuário) para permitir commits, já que o ambiente não tinha nenhuma configurada.
- Contradição encontrada no SPEC.md entre "Decisões de precisão numérica" (2 casas decimais) e a
  tabela de "Tipos de dados canônicos" (`numeric(19,4)`). Levada ao usuário, que decidiu manter
  **2 casas decimais** (`numeric(19,2)`) em todas as camadas. SPEC.md corrigido (tabela, exemplo de
  campo, e novo item 8 em "Premissas adotadas" registrando a decisão).
- A fórmula de deságio exige potência com expoente fracionário (`prazoDias/baseDias`), que
  `BigDecimal` não suporta nativamente (`pow` só aceita expoente inteiro) e que não pode ser feita
  em `double` (SPEC proíbe `double`/`float` para dinheiro e taxas). Adicionada a biblioteca
  `ch.obermuhlner:big-math` (`BigDecimalMath.pow`), que calcula potência fracionária via
  exp/log inteiramente em `BigDecimal` com `MathContext` configurável — sem perda de precisão e
  sem usar `double`. É uma dependência puramente matemática (sem I/O/framework), portanto não
  quebra a regra do domínio não depender de infraestrutura.
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
