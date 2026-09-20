package com.srmasset.creditengine.adapter.in.web.dto;

import com.srmasset.creditengine.application.port.out.RecebivelLeitura;
import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.Moeda;
import com.srmasset.creditengine.domain.Recebivel;
import com.srmasset.creditengine.domain.StatusRecebivel;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Campos BigDecimal levam {@code @Schema(type = "string")}: o swagger-core trata
 * BigDecimal como "number" por padrao, mas a API serializa como string (ver
 * JacksonConfig e SPEC.md, "Tipos de dados canonicos").
 */
public record RecebivelResponse(UUID id, String ativo,
                                 @Schema(type = "string", example = "15000.00") BigDecimal valorBruto,
                                 Moeda moeda, LocalDate dataVencimento, CategoriaRisco categoriaRisco,
                                 StatusRecebivel status,
                                 @Schema(type = "string", example = "14200.00") BigDecimal valorPresente,
                                 @Schema(type = "string", example = "800.00") BigDecimal valorDesagio,
                                 @Schema(type = "string", example = "0.146500") BigDecimal taxaDescontoAplicada,
                                 String motivoRejeicao,
                                 @Schema(description = "Moeda em que o recebivel e' efetivamente pago; "
                                         + "igual a `moeda` quando nao ha conversao cambial.")
                                 Moeda moedaPagamento,
                                 @Schema(type = "string", example = "5.20", description = "Cotacao (BRL por 1 "
                                         + "USD) usada na conversao; null quando moedaPagamento == moeda.")
                                 BigDecimal cotacaoCambio) {

    public static RecebivelResponse from(Recebivel recebivel) {
        return new RecebivelResponse(recebivel.getId(), recebivel.getAtivo(), recebivel.getValorBruto(),
                recebivel.getMoeda(), recebivel.getDataVencimento(), recebivel.getCategoriaRisco(),
                recebivel.getStatus(), recebivel.getValorPresente(), recebivel.getValorDesagio(),
                recebivel.getTaxaDescontoAplicada(), recebivel.getMotivoRejeicao(),
                recebivel.getMoedaPagamento(), recebivel.getCotacaoCambio());
    }

    public static RecebivelResponse from(RecebivelLeitura recebivel) {
        return new RecebivelResponse(recebivel.id(), recebivel.ativo(), recebivel.valorBruto(),
                recebivel.moeda(), recebivel.dataVencimento(), recebivel.categoriaRisco(),
                recebivel.status(), recebivel.valorPresente(), recebivel.valorDesagio(),
                recebivel.taxaDescontoAplicada(), recebivel.motivoRejeicao(),
                recebivel.moedaPagamento(), recebivel.cotacaoCambio());
    }
}
