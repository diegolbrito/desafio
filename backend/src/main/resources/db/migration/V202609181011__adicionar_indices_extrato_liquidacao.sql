-- Suporte a performance do extrato de liquidacao (relatorio via SQL nativo,
-- ver SPEC.md item 12): filtro por periodo + ordenacao sobre liquidado_em,
-- restrito aos recebiveis LIQUIDADOS (indice parcial - menor e mais rapido
-- que indexar a tabela inteira, ja que so' esse subconjunto interessa aqui).
create index ix_recebivel_liquidado_em on recebivel (liquidado_em desc) where status = 'LIQUIDADO';

-- Filtro de "ativo" no extrato e' busca parcial case-insensitive (contains);
-- um indice btree comum nao ajuda em "ILIKE '%...%'" - trigram (pg_trgm,
-- extensao padrao/contrib do Postgres, sem dependencia externa) sim.
create extension if not exists pg_trgm;
create index ix_recebivel_ativo_trgm on recebivel using gin (ativo gin_trgm_ops);
