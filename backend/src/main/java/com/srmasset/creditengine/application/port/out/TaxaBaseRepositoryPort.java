package com.srmasset.creditengine.application.port.out;

import com.srmasset.creditengine.application.exception.ReferenciaNaoEncontradaException;
import com.srmasset.creditengine.domain.Moeda;

import java.math.BigDecimal;

public interface TaxaBaseRepositoryPort {

    /**
     * @throws ReferenciaNaoEncontradaException se nao houver taxa base vigente cadastrada para a moeda.
     */
    BigDecimal buscarTaxaVigente(Moeda moeda);
}
