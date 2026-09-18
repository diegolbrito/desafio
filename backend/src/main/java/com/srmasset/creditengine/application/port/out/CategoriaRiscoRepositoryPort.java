package com.srmasset.creditengine.application.port.out;

import com.srmasset.creditengine.application.exception.ReferenciaNaoEncontradaException;
import com.srmasset.creditengine.domain.CategoriaRisco;

import java.math.BigDecimal;

public interface CategoriaRiscoRepositoryPort {

    /**
     * @throws ReferenciaNaoEncontradaException se nao houver spread cadastrado para a categoria.
     */
    BigDecimal buscarSpread(CategoriaRisco categoriaRisco);
}
