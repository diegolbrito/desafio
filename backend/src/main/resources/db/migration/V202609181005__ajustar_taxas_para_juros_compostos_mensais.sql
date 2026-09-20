-- A formula de desagio passou a aplicar a taxa ao mes, com prazo em meses inteiros e
-- juros compostos mensais (fatorDesconto = (1 + taxaDesconto) ^ prazoMeses), em vez de
-- taxa anual com expoente fracionario em dias corridos / base de dias por moeda
-- (ver SPEC.md, "Premissas adotadas" - item 1).
--
-- Os valores de referencia (proxies de mercado CDI/SOFR e spreads de risco) permanecem
-- os mesmos economicamente: convertidos aqui para o equivalente mensal via
-- taxaMensal = (1 + taxaAnual) ^ (1/12) - 1, arredondado em 6 casas (numeric(9,6)).

update taxa_base set taxa_vigente = 0.008469 where moeda = 'BRL'; -- 10,65% a.a. -> 0,8469% a.m.
update taxa_base set taxa_vigente = 0.003915 where moeda = 'USD'; -- 4,80% a.a. -> 0,3915% a.m.

update categoria_risco set spread_risco = 0.000830 where codigo = 'AA'; -- 1,0% a.a. -> 0,0830% a.m.
update categoria_risco set spread_risco = 0.001652 where codigo = 'A';  -- 2,0% a.a. -> 0,1652% a.m.
update categoria_risco set spread_risco = 0.002871 where codigo = 'B';  -- 3,5% a.a. -> 0,2871% a.m.
update categoria_risco set spread_risco = 0.004472 where codigo = 'C';  -- 5,5% a.a. -> 0,4472% a.m.
update categoria_risco set spread_risco = 0.006434 where codigo = 'D';  -- 8,0% a.a. -> 0,6434% a.m.
update categoria_risco set spread_risco = 0.009489 where codigo = 'E';  -- 12,0% a.a. -> 0,9489% a.m.
