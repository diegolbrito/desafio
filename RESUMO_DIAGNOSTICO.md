# RESUMO_DIAGNOSTICO.md — 500 em vez de 404 em rotas não mapeadas

**Reportado**: os endpoints `/actuator/env` e `/actuator/beans` (deliberadamente fora da lista de
exposição do actuator, ver `SPEC.md` item 10) retornam `500 Internal Server Error` em vez do `404`
esperado para uma rota não mapeada. Não há vazamento de dado, só o status HTTP não é semântico.

**Hipótese inicial**: incompatibilidade do Spring Boot 4.1 (versão bleeding-edge), na mesma linha
das demais já registradas no `PROGRESS.md`.

**Conclusão**: a hipótese não se confirma. O problema é do próprio código da aplicação, e o actuator
é apenas um sintoma incidental. Diagnóstico abaixo.

---

## A hipótese de incompatibilidade do Spring Boot 4.1 não se confirma

**Primeira evidência que descarta o actuator**: qualquer rota não mapeada devolve 500, não só as do
actuator.

| Requisição | Esperado | Real |
|---|---|---|
| `GET /actuator/env`, `/actuator/beans` | 404 | **500** |
| `GET /api/v1/rota-que-nao-existe` | 404 | **500** |
| `GET /qualquer-coisa` | 404 | **500** |
| `DELETE /api/v1/lotes-recebiveis` | 405 | **500** |
| `POST` com `Content-Type: text/plain` | 415 | **500** |
| `GET` com `Accept: application/xml` | 406 | **500** |

Ou seja: **o alcance é maior do que o reportado** — não é só o 404, são pelo menos quatro classes de
status quebradas. Os endpoints do actuator não expostos simplesmente não estão mapeados, então caem
no mesmo caminho de qualquer URL inexistente.

## Causa raiz

O log do backend mostra a exceção real:

```
ERROR c.s.c.a.i.w.e.GlobalExceptionHandler - Erro inesperado ao processar GET /actuator/env
org.springframework.web.servlet.resource.NoResourceFoundException: No static resource actuator/env
```

A cadeia é:

1. Path não mapeado → nenhum handler casa → a requisição cai no `ResourceHttpRequestHandler`
   (recursos estáticos), que lança `NoResourceFoundException`.
2. Essa exceção **já é uma `ErrorResponse` do Spring carregando status 404**, o framework sozinho
   responderia 404 corretamente.
3. Mas o Spring resolve exceções numa cadeia ordenada: `ExceptionHandlerExceptionResolver` (o que
   processa `@RestControllerAdvice`) → `ResponseStatusExceptionResolver` →
   `DefaultHandlerExceptionResolver` (o que traduziria para 404).
4. Como o [`GlobalExceptionHandler`](backend/src/main/java/com/srmasset/creditengine/adapter/in/web/exception/GlobalExceptionHandler.java)
   tem um `@ExceptionHandler(Exception.class)` e é um `@RestControllerAdvice` puro (não estende
   `ResponseEntityExceptionHandler`), **o catch-all vence na primeira etapa** e o
   `DefaultHandlerExceptionResolver` nunca é alcançado.

Resumindo: o handler genérico sombreia toda exceção de framework que já traz status semântico
próprio.

## Dois pontos que valem destaque

**O impacto não é puramente cosmético.** Todo 4xx desses é logado em `ERROR` com stack trace, e o
`http.server.requests` do Micrometer é tageado pelo status real da resposta. Um scanner varrendo
rotas gera uma enxurrada de logs de erro e infla a contagem de 5xx — que é justamente a métrica base
de alerta de SRE. A observabilidade fica contaminada por tráfego que deveria ser 404 silencioso.

## Correção recomendada

Fazer `GlobalExceptionHandler` estender `ResponseEntityExceptionHandler`. Essa classe base já traz
handlers dedicados para todas essas exceções de framework com o status correto, e por serem mais
específicos ganham do `Exception.class`, que fica como último recurso para o que é genuinamente
inesperado.

Ressalva: essa correção **foi diagnosticada, não testada**. O ponto de atenção é que
`ResponseEntityExceptionHandler` trabalha com `ResponseEntity<Object>` enquanto os handlers atuais
retornam `ProblemDetail`, então é preciso sobrescrever `handleExceptionInternal` para manter o
formato RFC 9457 e a política de log atual (WARN sem stack para 4xx, ERROR com stack para 5xx),
senão a padronização de erro que já existe regride.

---

## Como reproduzir

Com a stack no ar (`docker compose up -d`):

```bash
for p in /actuator/env /actuator/beans /api/v1/rota-que-nao-existe /qualquer-coisa; do
  printf "%-32s -> " "$p"; curl -s -o /dev/null -w "%{http_code}\n" "http://localhost:8080$p"
done

curl -s -o /dev/null -w "405 esperado -> %{http_code}\n" -X DELETE http://localhost:8080/api/v1/lotes-recebiveis
curl -s -o /dev/null -w "415 esperado -> %{http_code}\n" -X POST http://localhost:8080/api/v1/lotes-recebiveis -H "Content-Type: text/plain" -d "x"
curl -s -o /dev/null -w "406 esperado -> %{http_code}\n" http://localhost:8080/api/v1/lotes-recebiveis -H "Accept: application/xml"
```
