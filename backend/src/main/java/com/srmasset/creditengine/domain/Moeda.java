package com.srmasset.creditengine.domain;

/**
 * Moedas suportadas pelo fundo (caixa multimoedas - ver SPEC.md, secao
 * "Premissas adotadas" - item 3). Cada uma tem sua propria taxa base de
 * mercado cadastrada em {@code taxa_base}.
 */
public enum Moeda {
    BRL,
    USD
}
