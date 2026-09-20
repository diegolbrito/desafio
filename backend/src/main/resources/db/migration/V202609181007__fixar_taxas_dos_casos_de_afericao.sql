-- Fixa em migration os valores de referencia usados para validar os casos de
-- afericao fornecidos pelo negocio (ver SPEC.md, "Premissas adotadas" itens 1
-- e 3, e backend/.../domain/CasosAfericaoTest.java). Esses valores ja tinham
-- sido ajustados manualmente neste ambiente de desenvolvimento (fora de
-- migration), o que os deixava vulneraveis a um `docker compose down -v`
-- (o volume seria recriado com os valores antigos de V202609181005). Esta
-- migration versiona o estado correto para que qualquer ambiente novo
-- reproduza os mesmos casos de afericao.

-- Taxa base de 1% a.m. para as duas moedas (BRL e USD) - valor padrao
-- simplificado, substitui os proxies CDI/SOFR convertidos usados antes.
update taxa_base set taxa_vigente = 0.010000 where moeda = 'BRL';
update taxa_base set taxa_vigente = 0.010000 where moeda = 'USD';

-- Spreads de risco usados nos casos de afericao: AA soma com a taxa base
-- (1%) para dar 2,5% a.m. (casos C1/C3, "Duplicata Mercantil"); C soma para
-- dar 3,5% a.m. (caso C2, "Cheque Pre-datado"). Demais categorias mantidas.
update categoria_risco set spread_risco = 0.015000 where codigo = 'AA';
update categoria_risco set spread_risco = 0.025000 where codigo = 'C';
