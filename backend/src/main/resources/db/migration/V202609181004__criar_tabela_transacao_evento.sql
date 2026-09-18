-- Tabela de evento append-only (ver SPEC.md, convencao de banco de dados):
-- sem UPDATE/DELETE, por isso nao tem updated_at/version/deleted_at como as
-- demais tabelas de dominio - nada nela e' mutavel apos o insert.
create table transacao_evento (
    id uuid primary key default gen_random_uuid(),
    lote_recebivel_id uuid not null,
    recebivel_id uuid null,
    tipo varchar(30) not null,
    descricao varchar(1000) not null,
    ocorrido_em timestamptz not null,
    constraint fk_transacao_evento_lote_recebivel_id foreign key (lote_recebivel_id) references lote_recebivel (id),
    constraint fk_transacao_evento_recebivel_id foreign key (recebivel_id) references recebivel (id),
    constraint ck_transacao_evento_tipo check (tipo in
        ('LOTE_RECEBIDO', 'LOTE_PRECIFICADO', 'LOTE_ERRO', 'RECEBIVEL_PRECIFICADO', 'RECEBIVEL_REJEITADO'))
);

create index ix_transacao_evento_lote_recebivel_id on transacao_evento (lote_recebivel_id);
create index ix_transacao_evento_ocorrido_em on transacao_evento (ocorrido_em);
