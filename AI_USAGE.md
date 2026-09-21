# AI_USAGE.md — Uso de IA neste projeto

## Como o trabalho foi conduzido

Fluxo padrão em praticamente toda feature: a IA propõe um plano (arquivos afetados, decisões de
design, trade-offs), eu aprovo ou ajusto antes de qualquer código ser escrito, a IA implementa e roda
os testes de verdade (suite completa via Docker/Testcontainers, não só leitura do código), eu valido
manualmente quando faz sentido (subindo a stack via `docker-compose`, chamando os endpoints), e só
então vira commit/PR. Merge para `main` e criação de release são sempre uma ação manual minha,
nunca automática, mesmo quando a IA implementou, testou e abriu o PR sozinha.

## Um caso concreto em que a IA errou

**O quê**: a convenção de soft delete (`deleted_at`, "nada é deletado fisicamente em tabela de
domínio") foi implementada cedo no projeto, as colunas não existiam em todas as tabelas de domínio.
O que a IA nunca fez foi **aplicar** essa convenção: nenhum repositório filtrava `deleted_at is
null` em nenhuma consulta. Um registro "soft-deletado" continuava aparecendo normalmente em todo
`findById`/`findAll`/query — ou seja, a coluna existia, mas o soft delete não tinha efeito nenhum na
prática, em nenhuma das quatro entidades de domínio.

**Por que isso não foi pego antes**: todos os testes existentes passavam. Nenhum teste exercitava o
cenário "buscar um registro que foi deletado e confirmar que ele não aparece", porque esse
cenário nunca tinha sido escrito, já que a funcionalidade de deletar nunca tinha sido de fato
implementada com esse rigor. A IA, tendo escrito a coluna, não auditou
proativamente se ela era respeitada nas leituras, um ponto cego que só aparece quando alguém
pergunta especificamente sobre ele.

**Como foi detectado**: não por um teste automatizado, nem por revisão de código da IA. Foi uma
pergunta direta e específica de domínio ("nas tabelas que utilizam soft delete, os repositórios
levam em conta se o dado buscado/atualizado foi deletado?") que não tinha nenhum gatilho no código,
só o conhecimento de que "ter a coluna" e "a coluna ser respeitada" são coisas diferentes.

**Correção**: `@SQLRestriction("deleted_at is null")` nas quatro entidades JPA, mais um teste que
prova as duas pontas — que um registro soft-deletado deixa de ser encontrado pelos finders padrão
(`findById`, `findByCodigo`, etc.), *e* que ele continua fisicamente na tabela (verificado via query
nativa, contornando o Hibernate), sem essa segunda ponta, o teste poderia "passar" mesmo se a
correção tivesse virado um delete físico por engano.

## O que decidi não delegar à IA

- **Merge de PR e release**: em toda feature deste projeto, a IA pode implementar, testar e abrir o
  PR sozinha, mas o merge para `main` e a criação de release são sempre uma ação manual minha.
- **Premissas de negócio não especificadas**: onde a spec original não definia uma regra,
  pedi que a IA registrasse a suposição explicitamente como premissa assumida (`SPEC.md`, seção 
  "Premissas adotadas") em vez de simplesmente implementar uma escolha silenciosa, a diferença entre 
  "a IA decidiu" e "a IA decidiu e isso está documentado onde dá pra eu revisar depois" foi deliberada.
- **Decisões estratégicas de arquitetura**: escolha de banco (SQL vs NoSQL), de topologia (um
  serviço vs microserviços) e de execução (síncrono vs assíncrono) foram pedidas por mim como um
  ADR (`ADR.md`) com justificativa explícita, não aceitas apenas porque a IA sugeriu, o objetivo era
  ter o racional por escrito para eu poder discordar depois, se fizesse sentido.
