package com.srmasset.creditengine.application.port.out;

import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.Moeda;
import com.srmasset.creditengine.domain.StatusRecebivel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Projecao somente-leitura de um recebivel persistido, usada pelas consultas (GET). */
public record RecebivelLeitura(UUID id, String cedente, BigDecimal valorBruto, Moeda moeda,
                                LocalDate dataVencimento, CategoriaRisco categoriaRisco,
                                StatusRecebivel status, BigDecimal valorPresente,
                                BigDecimal valorDesagio, BigDecimal taxaDescontoAplicada,
                                String motivoRejeicao) {
}
