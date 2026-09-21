package com.srmasset.creditengine.application.port.out;

import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.Moeda;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Uma linha do extrato de liquidacao - sempre um recebivel com status LIQUIDADO. */
public record ExtratoLiquidacaoItem(UUID recebivelId, UUID loteId, String ativo,
                                     BigDecimal valorBruto, BigDecimal valorPresente,
                                     BigDecimal valorDesagio, Moeda moedaPagamento,
                                     BigDecimal cotacaoCambio, CategoriaRisco categoriaRisco,
                                     LocalDate dataVencimento, OffsetDateTime liquidadoEm) {
}
