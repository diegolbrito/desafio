package com.srmasset.creditengine.application.port.out;

import java.math.BigDecimal;

/**
 * Cotacao de cambio usada na conversao cross-currency (ver SPEC.md, "Premissas
 * adotadas" item 11). Unico par suportado hoje: quantidade de BRL por 1 USD.
 */
public interface CotacaoCambioPort {

    BigDecimal buscarCotacao();
}
