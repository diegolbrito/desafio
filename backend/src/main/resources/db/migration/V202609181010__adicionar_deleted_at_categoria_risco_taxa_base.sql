-- categoria_risco e taxa_base sao tabelas de dominio (referencia), nao a
-- excecao append-only (essa e' so' transacao_evento) - por isso devem seguir
-- a convencao padrao do SPEC.md ("Banco de dados"): nada e' deletado
-- fisicamente, soft delete via deleted_at. Faltava nas duas desde a criacao
-- (V202609181000/V202609181001) - corrigido aqui, sem alterar as migrations
-- originais ja aplicadas.

alter table categoria_risco add column deleted_at timestamptz null;
alter table taxa_base add column deleted_at timestamptz null;
