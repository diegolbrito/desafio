package com.srmasset.creditengine.domain;

/**
 * Categoria de risco de credito atribuida ao recebivel na entrada do lote
 * (ver SPEC.md, secao "Premissas adotadas" - item 2). O spread associado a
 * cada categoria e' dado de referencia (tabela categoria_risco), nao vive
 * no dominio.
 */
public enum CategoriaRisco {
    AA,
    A,
    B,
    C,
    D,
    E
}
