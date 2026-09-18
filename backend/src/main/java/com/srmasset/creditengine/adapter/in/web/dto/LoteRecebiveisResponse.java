package com.srmasset.creditengine.adapter.in.web.dto;

import com.srmasset.creditengine.application.port.out.LoteRecebiveisDetalhe;
import com.srmasset.creditengine.domain.LoteRecebiveis;
import com.srmasset.creditengine.domain.StatusLote;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record LoteRecebiveisResponse(UUID id, LocalDate dataReferencia, StatusLote status,
                                      List<RecebivelResponse> recebiveis) {

    public static LoteRecebiveisResponse from(LoteRecebiveis lote) {
        List<RecebivelResponse> recebiveis = lote.getRecebiveis().stream()
                .map(RecebivelResponse::from)
                .toList();
        return new LoteRecebiveisResponse(lote.getId(), lote.getDataReferencia(), lote.getStatus(), recebiveis);
    }

    public static LoteRecebiveisResponse from(LoteRecebiveisDetalhe detalhe) {
        List<RecebivelResponse> recebiveis = detalhe.recebiveis().stream()
                .map(RecebivelResponse::from)
                .toList();
        return new LoteRecebiveisResponse(detalhe.id(), detalhe.dataReferencia(), detalhe.status(), recebiveis);
    }
}
