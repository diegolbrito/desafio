create table categoria_risco (
    id uuid primary key default gen_random_uuid(),
    codigo varchar(2) not null,
    spread_risco numeric(9,6) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint uq_categoria_risco_codigo unique (codigo)
);

-- Categorias e spreads de mercado assumidos (ver SPEC.md, "Premissas adotadas" - item 2).
insert into categoria_risco (codigo, spread_risco) values
    ('AA', 0.010000),
    ('A',  0.020000),
    ('B',  0.035000),
    ('C',  0.055000),
    ('D',  0.080000),
    ('E',  0.120000);
