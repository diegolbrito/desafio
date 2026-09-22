# POSTMORTEM.md - Liquidações duplicadas

**Exercício de simulação.** Incidente fictício, conduzido como treino de resposta. O código do
Anexo A é o mesmo analisado em [`REVIEW.md`](REVIEW.md) e não pertence a este repositório.

| | |
|---|---|
| **Severidade** | SEV-1 (perda financeira direta, irreversível sem ação de negócio) |
| **Detecção** | Reporte manual da mesa de operações, nenhum alerta disparou |
| **Tempo até detecção** | ~14 dias desde o deploy (não ~35 minutos) |
| **Plantão** | 1 Staff Engineer (coordenação) + 1 dev pleno (execução) |
| **Postura** | Blameless. O defeito é do sistema que deixou o código chegar em produção, não de quem escreveu ou aprovou. |

**Resumo**: três cedentes receberam a mesma liquidação duas vezes. O endpoint `POST /settlements`
não tem nenhuma garantia de unicidade, nem no código, nem no banco, e responde `200 {ok: true}`
mesmo quando a escrita falha pela metade. O número reportado (3) é o que a mesa enxergou, não o
tamanho do incidente.

---

## 1. Linha do tempo

**D-14, sexta** PR do endpoint mergeado e deployado. Sem teste de chamada repetida, sem constraint
de unicidade, sem log no caminho de erro.

**D-14 a D-1** Liquidações ocorrem normalmente na maior parte dos casos. Duplicatas isoladas
provavelmente já acontecem, mas são invisíveis: a API sempre responde `200`, então não há sinal de
erro em nenhuma métrica ou log. Ninguém concilia contagem de liquidações contra contagem de
recebíveis liquidados.

**D0, 17h50** Janela de fechamento de sexta, pico de volume na mesa.

**D0, ~18h05** Um gatilho transitório atinge o caminho de escrita: contenção de lock no `UPDATE`
de `receivables`, timeout de conexão, ou lentidão do `fxService` segurando a requisição por tempo
suficiente para o cliente desistir. Para algumas requisições, o `INSERT` em `settlements` conclui e o
`UPDATE ... SET status = 'SETTLED'` falha. O `catch` vazio engole a exceção, literalmente comentado
como *"se falhar aqui, o insert já rodou, então segue o jogo"*, e a API responde `200 {ok: true}`.

**D0, 18h05–18h30** Na tela da mesa, esses recebíveis continuam aparecendo como **não liquidados**,
porque o status nunca mudou. O operador faz exatamente o que a interface pede: clica em liquidar de
novo. O endpoint não verifica o status atual em nenhum momento, ele apenas insere outra linha. Segunda
liquidação registrada.

**D0, 18h35** O lote de pagamento é gerado a partir de `settlements` e transmitido. Três cedentes
recebem em duplicidade.

**D0, 18h40** A mesa reporta.

O ponto central da linha do tempo: **o sistema induziu o operador ao erro**. A duplicação não exigiu
concorrência nem má-fé, exigiu apenas que alguém confiasse na tela.

## 2. Causa raiz provável e como confirmar

**Causa raiz**: ausência total de garantia de idempotência no caminho de liquidação. Não há
constraint de unicidade em `settlements`, não há verificação de status antes de inserir, e o par
`INSERT` + `UPDATE` não é atômico. Qualquer segunda chamada, retry de rede, duplo clique, ou
operador reagindo a uma tela desatualizada, gera um segundo pagamento.

**Causa contribuinte**: o `catch` vazio, que transforma uma falha parcial em sucesso aparente e
remove a única evidência que permitiria detectar o problema.

### Evidências para confirmar

**Banco - existência e alcance** (rodar sobre os 14 dias, não só hoje):
```sql
SELECT receivable_id, count(*) AS vezes,
       array_agg(amount ORDER BY created_at), array_agg(created_at ORDER BY created_at)
FROM settlements GROUP BY receivable_id HAVING count(*) > 1;
```

**Banco - qual mecanismo**, pelo intervalo entre as duas linhas:
- Δ abaixo de ~2s e valores idênticos → retry de cliente/gateway ou duplo clique.
- Δ de minutos → hipótese principal: operador reliquidando porque a tela mostrava pendente.
- Valores **diferentes** para o mesmo recebível → confirma duas execuções independentes em momentos
  distintos com a cotação de câmbio tendo variado entre elas (caminho USD).

**Logs**: o `catch` vazio significa que **a evidência que mais queríamos não existe**. Isso já é um
achado. Resta: log de acesso do proxy (dois `POST /settlements` com mesmo corpo, ambos `200`), log do
Postgres na janela (`lock timeout`, `deadlock detected`), e latência/erro do provedor de câmbio às 18h.

**Métricas**: esperamos encontrar **zero sinal**, todas as respostas foram `200`. A confirmação de
que o monitoramento era cego por construção vale tanto quanto a confirmação da causa.

**Verificação adicional obrigatória**: recalcular o valor esperado de cada liquidação dos 14 dias e
comparar com o registrado. O `BASE_RATE = 1.0` (100% ao mês) do Anexo A sugere que os valores podem
estar errados para **todos** os cedentes, não só duplicados para três. Se confirmado, a duplicação é
o sintoma visível de um incidente maior.

## 3. Contenção vs. correção definitiva

### Contenção hoje, sexta, 18h40, com duas pessoas

Ordem importa. Dinheiro primeiro, código depois.

1. **Parar o que ainda não saiu.** Verificar se há lote de pagamento pendente de transmissão ou
   agendado para segunda. O que já foi pago é conversa de recuperação; o que está na fila ainda é
   evitável em minutos.
2. **Congelar o caminho.** Desligar o endpoint por feature flag ou reverter o deploy. Em uma sexta
   à noite, com duas pessoas cansadas, **reverter é mais seguro que corrigir**. A mesa opera em
   processo manual até segunda.
3. **Dimensionar.** Dev pleno roda a query de duplicatas sobre os 14 dias e reconcilia com o que foi
   efetivamente pago. O número real quase certamente é maior que três.
4. **Acionar negócio.** Tesouraria e quem fala com os cedentes. Recuperação de valor pago é decisão
   de negócio, não técnica, e começa antes de segunda.
5. **Divisão de papéis.** Staff coordena, comunica e decide; dev pleno executa consultas. Os dois
   mergulhados no código ao mesmo tempo é como se perde o controle do incidente.

**Explicitamente fora do escopo de hoje**: corrigir SQL injection, refatorar o endpoint, ajustar a
fórmula. Tudo isso é real e urgente, nenhum deles melhora com duas pessoas exaustas numa sexta.

### Correção definitiva - segunda, com o time inteiro

1. **Constraint de unicidade em `settlements(receivable_id)`.** Esta é a correção principal: a
   invariante passa a ser garantida pelo banco, não pela disciplina de quem escreve o próximo
   endpoint. Violação da constraint é tratada como sucesso idempotente (retorna a liquidação
   existente, não cria outra).
2. **Transação única** envolvendo `INSERT` + `UPDATE`, com verificação de status dentro dela e lock
   da linha. Falha → rollback e erro HTTP real.
3. **Remover o `catch` vazio.** Erro propaga; a API nunca responde `200` para escrita que não
   aconteceu.
4. **Queries parametrizadas** e **aritmética decimal exata**, já estamos tocando as três queries e o
   cálculo, e ambos são críticos por mérito próprio (ver `REVIEW.md`).
5. **Teste que chama o endpoint duas vezes** e prova que há uma liquidação só. Sem esse teste, a
   correção não entra.

## 4. Prevenção sistêmica

O objetivo é que essa classe de erro morra por construção, não por vigilância. Vigilância cansa;
constraint não.

**Design - a invariante vive no banco.** Toda operação que move dinheiro ganha unicidade declarada no
schema. Um `UNIQUE` não depende de o próximo dev lembrar, de o revisor estar atento, nem de a IA ter
gerado o código certo. É a única barreira que funciona igual às 18h40 de sexta e às 10h de terça.

**Detecção - o gap de 14 dias é o achado mais grave deste incidente.** Mais grave que a duplicação em
si: o sistema não tinha como avisar. Uma rotina diária de conciliação, total de liquidações versus
total de recebíveis liquidados, alertando na divergência, teria pego isso no primeiro dia, mesmo com
o código exatamente como está. É barato, roda sozinha e independe de qualquer mudança no endpoint.

**Pipeline - gates automáticos, não formulários.** Lint que barra `catch` vazio; análise estática que
barra SQL por interpolação de string. Duas regras, custo zero por PR, e cobrem os dois piores
defeitos do Anexo A sem consumir atenção humana.

**Processo - rigor proporcional ao raio de alcance.** A resposta errada aqui é "mais revisão para
todo mundo", que desacelera o time inteiro por causa dos 5% de código que perdem dinheiro. A resposta
certa é marcar explicitamente os caminhos financeiros e exigir deles, e só deles, um segundo revisor
e quatro perguntas: *o que acontece se chamar duas vezes? o que acontece se a segunda escrita falhar?
o cliente consegue distinguir sucesso de falha? qual invariante o banco garante?* Quatro perguntas,
não um checklist de quarenta itens.

**Sobre proibir deploy na sexta**: é a conclusão fácil e, aqui, a errada. O defeito sobreviveu
quatorze dias, sexta-feira não causou o incidente, apenas concentrou o volume que o tornou visível.
Investir em detecção vale mais que criar um tabu de calendário que o time vai contornar na primeira
urgência.

**Sobre o código ter sido gerado por IA**: irrelevante como causa. Código gerado por IA e código
escrito às pressas por humano falham igual, o que falhou foi o gate ter sido pulado. As mesmas duas
regras de lint, a mesma constraint e a mesma pergunta "e se chamar duas vezes?" pegariam os dois.

---

## Ações

| # | Ação | Tipo | Dono | Prazo |
|---|---|---|---|---|
| 1 | Bloquear lote de pagamento pendente | Contenção | Staff | Imediato |
| 2 | Reverter/desligar o endpoint | Contenção | Dev pleno | Imediato |
| 3 | Dimensionar duplicatas nos 14 dias e reconciliar | Contenção | Dev pleno | Hoje |
| 4 | Acionar tesouraria para recuperação | Negócio | Staff | Hoje |
| 5 | Verificar se os **valores** também estão errados (`BASE_RATE`) | Investigação | Staff | Segunda |
| 6 | Constraint de unicidade + transação + teste de chamada dupla | Correção | Time | Segunda |
| 7 | Conciliação diária com alerta | Prevenção | Time | 1 semana |
| 8 | Lint: `catch` vazio e SQL interpolado | Prevenção | Time | 1 semana |
| 9 | Marcar caminhos financeiros + as 4 perguntas de revisão | Processo | Staff | 2 semanas |
