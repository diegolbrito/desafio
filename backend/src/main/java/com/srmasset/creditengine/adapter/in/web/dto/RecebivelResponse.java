package com.srmasset.creditengine.adapter.in.web.dto;

import com.srmasset.creditengine.application.port.out.RecebivelLeitura;
import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.Moeda;
import com.srmasset.creditengine.domain.Recebivel;
import com.srmasset.creditengine.domain.StatusRecebivel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record RecebivelResponse(UUID id, String cedente, BigDecimal valorBruto, Moeda moeda,
                                 LocalDate dataVencimento, CategoriaRisco categoriaRisco,
                                 StatusRecebivel status, BigDecimal valorPresente,
                                 BigDecimal valorDesagio, BigDecimal taxaDescontoAplicada,
                                 String motivoRejeicao) {

    public static RecebivelResponse from(Recebivel recebivel) {
        return new RecebivelResponse(recebivel.getId(), recebivel.getCedente(), recebivel.getValorBruto(),
                recebivel.getMoeda(), recebivel.getDataVencimento(), recebivel.getCategoriaRisco(),
                recebivel.getStatus(), recebivel.getValorPresente(), recebivel.getValorDesagio(),
                recebivel.getTaxaDescontoAplicada(), recebivel.getMotivoRejeicao());
    }

    public static RecebivelResponse from(RecebivelLeitura recebivel) {
        return new RecebivelResponse(recebivel.id(), recebivel.cedente(), recebivel.valorBruto(),
                recebivel.moeda(), recebivel.dataVencimento(), recebivel.categoriaRisco(),
                recebivel.status(), recebivel.valorPresente(), recebivel.valorDesagio(),
                recebivel.taxaDescontoAplicada(), recebivel.motivoRejeicao());
    }
}
