package com.srmasset.creditengine.domain;

import java.math.BigDecimal;

/**
 * Resultado do calculo de desagio de um recebivel: valores ja arredondados
 * (HALF_EVEN) na escala final (ver SPEC.md, "Premissas adotadas" - item 8).
 */
public record ResultadoDesagio(BigDecimal valorPresente, BigDecimal valorDesagio, BigDecimal taxaDescontoAplicada) {
}
