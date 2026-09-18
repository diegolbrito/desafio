create table recebivel (
    id uuid primary key default gen_random_uuid(),
    lote_recebivel_id uuid not null,
    cedente varchar(255) not null,
    valor_bruto numeric(19,2) not null,
    moeda char(3) not null,
    data_vencimento date not null,
    categoria_risco varchar(2) not null,
    status varchar(30) not null,
    valor_presente numeric(19,2) null,
    valor_desagio numeric(19,2) null,
    taxa_desconto_aplicada numeric(9,6) null,
    motivo_rejeicao varchar(500) null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    deleted_at timestamptz null,
    constraint fk_recebivel_lote_recebivel_id foreign key (lote_recebivel_id) references lote_recebivel (id),
    constraint fk_recebivel_categoria_risco foreign key (categoria_risco) references categoria_risco (codigo),
    constraint ck_recebivel_valor_bruto check (valor_bruto > 0),
    constraint ck_recebivel_status check (status in ('PENDENTE', 'PRECIFICADO', 'REJEITADO'))
);

create index ix_recebivel_lote_recebivel_id on recebivel (lote_recebivel_id);
create index ix_recebivel_status on recebivel (status);
