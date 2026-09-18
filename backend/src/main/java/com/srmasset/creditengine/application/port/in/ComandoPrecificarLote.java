package com.srmasset.creditengine.application.port.in;

import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.Moeda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Entrada do caso de uso de precificacao de lote. A data de referencia da
 * precificacao nao e' informada pelo chamador: e' a data de entrada do lote
 * (ver SPEC.md, "Premissas adotadas" - item 1), resolvida pelo servico via Clock.
 */
public record ComandoPrecificarLote(List<ComandoRecebivel> recebiveis) {

    public record ComandoRecebivel(String cedente, BigDecimal valorBruto, Moeda moeda,
                                    LocalDate dataVencimento, CategoriaRisco categoriaRisco) {
    }
}
