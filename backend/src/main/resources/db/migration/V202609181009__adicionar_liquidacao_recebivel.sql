-- Nova feature: liquidacao de recebivel (ver SPEC.md, "Premissas adotadas" -
-- liquidacao). liquidado_em registra o instante da liquidacao (null enquanto
-- nao liquidado); o novo status LIQUIDADO e o novo tipo de evento
-- RECEBIVEL_LIQUIDADO precisam ser adicionados aos check constraints
-- existentes (nao podem ser alterados in-place, so' drop+add).

alter table recebivel add column liquidado_em timestamptz null;

alter table recebivel drop constraint ck_recebivel_status;
alter table recebivel add constraint ck_recebivel_status
    check (status in ('PENDENTE', 'PRECIFICADO', 'REJEITADO', 'LIQUIDADO'));

alter table transacao_evento drop constraint ck_transacao_evento_tipo;
alter table transacao_evento add constraint ck_transacao_evento_tipo check (tipo in
    ('LOTE_RECEBIDO', 'LOTE_PRECIFICADO', 'LOTE_ERRO', 'RECEBIVEL_PRECIFICADO', 'RECEBIVEL_REJEITADO',
     'RECEBIVEL_LIQUIDADO'));
