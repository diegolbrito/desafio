package com.srmasset.creditengine.adapter.in.web.dto;

import com.srmasset.creditengine.application.port.out.ExtratoLiquidacaoItem;
import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.Moeda;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record ExtratoLiquidacaoItemResponse(UUID recebivelId, UUID loteId, String ativo,
                                             @Schema(type = "string", example = "15000.00") BigDecimal valorBruto,
                                             @Schema(type = "string", example = "14200.00") BigDecimal valorPresente,
                                             @Schema(type = "string", example = "800.00") BigDecimal valorDesagio,
                                             Moeda moedaPagamento,
                                             @Schema(type = "string", example = "5.20") BigDecimal cotacaoCambio,
                                             CategoriaRisco categoriaRisco, LocalDate dataVencimento,
                                             OffsetDateTime liquidadoEm) {

    public static ExtratoLiquidacaoItemResponse from(ExtratoLiquidacaoItem item) {
        return new ExtratoLiquidacaoItemResponse(item.recebivelId(), item.loteId(), item.ativo(),
                item.valorBruto(), item.valorPresente(), item.valorDesagio(), item.moedaPagamento(),
                item.cotacaoCambio(), item.categoriaRisco(), item.dataVencimento(), item.liquidadoEm());
    }
}
