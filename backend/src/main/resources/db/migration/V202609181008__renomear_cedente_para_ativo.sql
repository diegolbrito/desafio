-- "cedente" renomeado para "ativo": nome mais adequado ao dominio (o campo
-- identifica o ativo/instrumento do recebivel - duplicata, cheque, etc. -
-- nao a empresa cedente em si, que nao tem cadastro proprio no MVP; ver
-- SPEC.md, "Premissas adotadas" item 7).

alter table recebivel rename column cedente to ativo;
