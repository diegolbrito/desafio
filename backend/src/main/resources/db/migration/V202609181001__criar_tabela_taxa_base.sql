create table taxa_base (
    id uuid primary key default gen_random_uuid(),
    moeda char(3) not null,
    taxa_vigente numeric(9,6) not null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    version integer not null default 0,
    constraint uq_taxa_base_moeda unique (moeda)
);

-- Taxas base de mercado assumidas (proxy CDI/SOFR - ver SPEC.md, "Premissas adotadas" - item 3).
insert into taxa_base (moeda, taxa_vigente) values
    ('BRL', 0.106500),
    ('USD', 0.048000);
