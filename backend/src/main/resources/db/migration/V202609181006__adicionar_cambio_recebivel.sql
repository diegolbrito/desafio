-- Suporte a cross-currency (titulo numa moeda, pagamento em outra) - ver SPEC.md,
-- "Premissas adotadas" item 3. moeda_pagamento acompanha cada recebivel (default a
-- propria moeda do titulo, sem conversao); cotacao_cambio so e' preenchida quando ha
-- conversao (recebida por parametro na API, nao cadastrada em tabela de referencia,
-- pois cotacao de cambio muda em tempo real).

alter table recebivel add column moeda_pagamento char(3);
update recebivel set moeda_pagamento = moeda;
alter table recebivel alter column moeda_pagamento set not null;

alter table recebivel add column cotacao_cambio numeric(9,6) null;

alter table recebivel add constraint ck_recebivel_cotacao_cambio check (
    (moeda_pagamento = moeda and cotacao_cambio is null)
    or (moeda_pagamento <> moeda and cotacao_cambio > 0)
);
