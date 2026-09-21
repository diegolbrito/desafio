# REVIEW.md — `POST /settlements` (settlement.controller.ts)

**Contexto**: endpoint de liquidação gerado por IA e mergeado sem revisão numa sexta-feira. Este
documento é o review que deveria ter acontecido antes do merge — o código já está em produção, então
as recomendações abaixo são tratadas como um incidente em aberto, não uma sugestão para "quando
sobrar tempo".

## Veredito

**Bloqueante. Recomendo reverter ou desativar a rota agora (feature flag / revert do merge) e tratar
como incidente**, não como débito técnico. Há pelo menos três defeitos que resultam em **dinheiro
pago em duplicidade ou perdido silenciosamente**, e um deles (injeção de SQL) é explorável por
qualquer cliente da API. Nenhum teste automatizado cobre este arquivo.

---

## Críticos (bloqueiam merge / exigem hotfix imediato)

### 1. Injeção de SQL nas três queries
```ts
`SELECT * FROM receivables WHERE id = ${receivableId}`
`INSERT INTO settlements (receivable_id, amount, currency) VALUES (${receivableId}, ${finalAmount.toFixed(2)}, '${currency}')`
`UPDATE receivables SET status = 'SETTLED' WHERE id = ${receivableId}`
```
`receivableId` e `currency` vêm direto de `req.body` e são concatenados como string em todas as
queries, incluindo a de escrita. Um payload como
`{"receivableId": "1; UPDATE receivables SET status='SETTLED'; --", "currency": "BRL"}` (ou, no
campo `currency`, `"BRL', (SELECT ... )-- "`) processa qualquer SQL arbitrário com os mesmos
privilégios da aplicação — leitura, alteração ou exclusão de qualquer tabela acessível.
**Correção**: queries parametrizadas (`$1`/`$2` ou o equivalente do driver/ORM em uso) nas três
chamadas, sem exceção. Isso não é opcional nem específico deste endpoint, se o mesmo padrão existe
em outro lugar da base, precisa de uma varredura.

### 2. Erro de escrita é engolido e a API mente para o cliente
```ts
try {
  await db.query(`INSERT INTO settlements ...`);
  await db.query(`UPDATE receivables SET status = 'SETTLED' ...`);
} catch (e) {
  // se falhar aqui, o insert já rodou, então segue o jogo
}
res.status(200).json({ ok: true, amount: finalAmount.toFixed(2) });
```
Qualquer exceção nas duas escritas é descartada e a rota **sempre** responde `200 { ok: true }`,
mesmo quando nada foi persistido (ou só metade foi). Cenário concreto: o `INSERT` em `settlements`
funciona, mas o `UPDATE` falha (deadlock, timeout, constraint), o comentário já documenta a decisão
consciente de ignorar isso. Resultado: existe um registro de liquidação (dinheiro "pago"), mas o
recebível continua sem status `SETTLED` e pode ser liquidado de novo depois, pagando o mesmo
recebível duas vezes. O cliente que chamou a API nunca fica sabendo que algo deu errado.
**Correção**: as duas escritas devem ser atômicas (uma transação: `BEGIN` / `COMMIT` / `ROLLBACK`),
e uma falha deve propagar como erro HTTP real (5xx), nunca um `200`
fabricado.

### 3. Sem idempotência nem trava de concorrência — liquidação duplicada
Não há transação, não há `SELECT ... FOR UPDATE` (ou lock otimista) e não há verificação do status
atual do recebível antes de liquidar. Duas chamadas concorrentes para o mesmo `receivableId` (retry
de rede, duplo clique, ou duas réplicas da API processando a mesma fila) leem o mesmo estado
"ainda não liquidado" e ambas inserem uma linha em `settlements` — pagamento duplicado. Mesmo sem
concorrência: nada impede chamar esta rota N vezes para o mesmo recebível e gerar N liquidações.
**Correção**: checar o status do recebível dentro da mesma transação com lock (pessimista ou
otimista via coluna de versão), e tornar a operação idempotente — uma segunda chamada para um
recebível já liquidado deve retornar o resultado existente, não criar um novo.

---

## Altos

### 4. `receivable` nulo derruba a requisição sem tratamento
`db.queryOne` pode retornar `null`/`undefined` se o `id` não existir (ou, hoje, se a injeção de SQL
alterar a query o suficiente para não casar nada). A linha seguinte, `receivable.type`, lança uma
exceção dentro de um handler `async` sem `try/catch` ao redor, em Express isso vira uma promise
rejeitada não tratada: dependendo da configuração do processo, a requisição fica pendurada sem
resposta ou o processo Node derruba por completo. Não há nem um `404` para "recebível não existe".

### 5. Dinheiro calculado em ponto flutuante binário (`number` + `toFixed`)
`face_value / Math.pow(...)` e `.toFixed(2)` fazem toda a conta em `double`.
`toFixed` não é arredondamento bancário (HALF_EVEN) e tem bugs conhecidos de precisão (ex.:
`(1.005).toFixed(2)` retorna `"1.00"`, não `"1.01"`, por erro de representação binária). Num sistema
que move dinheiro, isso é diferença de centavos que se acumula e não bate com a contabilidade,
precisa de um tipo decimal exato (`decimal.js`, `big.js`, ou o tipo `numeric` do banco tratado como
string do início ao fim, nunca convertido para `number` no meio do caminho).

### 6. `BASE_RATE` e spread cravados no código, sem relação com moeda ou risco
```ts
const BASE_RATE = 1.0; // taxa base mensal
const spread = receivable.type === "DUPLICATA" ? 1.5 : 2.5;
```
`BASE_RATE = 1.0` como taxa mensal é 100% ao mês, um valor absurdo para qualquer instrumento real
(muito provavelmente um erro de escala, faltando dividir por 100, ou um placeholder que nunca foi
substituído). O spread também é fixo por dois valores hardcoded, ignorando categoria de risco e
moeda. Nenhum dos dois vem de configuração/banco, e nenhum teste teria pego isso porque não há
teste. Isso não é só estilo: é a fórmula financeira central do endpoint estar, aparentemente, errada.

---

## Médios

### 7. Sem validação de entrada
`receivableId` e `currency` não são validados (tipo, formato, presença). Um `currency` fora de
`BRL`/`USD` não cai em nenhum erro, simplesmente pula a conversão cambial e trata o valor original
como se já estivesse na moeda pedida, e esse valor arbitrário ainda é interpolado na query de
`INSERT` (ver item 1). Faltam validação de schema antes de tocar no banco.

### 8. Nenhuma verificação do status do recebível antes de liquidar
Não há checagem de que o recebível está num estado que permite liquidação (ex.: precificado e ainda
não liquidado). Um recebível pendente, rejeitado ou já liquidado é processado do mesmo jeito.

### 9. Direção da conversão cambial não é validada nem testada
`finalAmount = presentValue / rate` assume uma direção específica de cotação (ex.: BRL por 1 USD).
Se `fxService.getLatestRate` alguma vez retornar a taxa no sentido inverso, o valor final fica errado,
sem nenhum teste ou assert protegendo essa suposição.

## Baixos
- Números mágicos (`1.5`, `2.5`, `1.0`) sem nome/constante documentando a origem.
- Nenhum log/evento de auditoria da liquidação (se o restante do sistema tem trilha auditável, este
  endpoint quebra esse padrão).
- Não dá para ver autenticação/autorização neste trecho, vale confirmar que não é um endpoint
  aberto movendo dinheiro sem controle de acesso.

---

## Ação recomendada
1. **Agora**: reverter o merge ou desativar a rota (feature flag), dado que já está em produção e os
   itens 1–3 são exploráveis/custam dinheiro real.
2. **Hotfix**: queries parametrizadas + transação atômica (insert + update) + checagem de status +
   idempotência, com teste de integração cobrindo o caminho feliz, o caso de moeda inválida, o caso
   de recebível inexistente/já liquidado, e no mínimo um teste que prove que duas chamadas
   sequenciais para o mesmo `receivableId` não duplicam o pagamento.
3. **Antes do próximo deploy**: revisar se `BASE_RATE`/spread deveriam vir de configuração/tabela em
   vez de constante no código, e trocar a aritmética monetária para um tipo decimal exato.
