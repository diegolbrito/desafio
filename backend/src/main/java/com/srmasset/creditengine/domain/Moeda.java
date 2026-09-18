package com.srmasset.creditengine.domain;

/**
 * Base de dias de cada moeda conforme convenção de mercado adotada
 * (ver SPEC.md, secao "Premissas adotadas" - item 1).
 */
public enum Moeda {

    BRL(252),
    USD(360);

    private final int baseDias;

    Moeda(int baseDias) {
        this.baseDias = baseDias;
    }

    public int baseDias() {
        return baseDias;
    }
}
