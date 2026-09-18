create table lote_recebivel (
    id uuid primary key default gen_random_uuid(),
    data_referencia date not null,
    status varchar(30) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    deleted_at timestamptz null,
    constraint ck_lote_recebivel_status check (status in ('RECEBIDO', 'PRECIFICADO', 'ERRO'))
);

create index ix_lote_recebivel_status on lote_recebivel (status);
create index ix_lote_recebivel_created_at on lote_recebivel (created_at);
