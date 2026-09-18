package com.srmasset.creditengine.adapter.in.web.dto;

import com.srmasset.creditengine.application.port.out.LoteRecebiveisResumo;
import com.srmasset.creditengine.domain.StatusLote;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LoteRecebiveisResumoResponse(UUID id, LocalDate dataReferencia, StatusLote status, OffsetDateTime createdAt) {

    public static LoteRecebiveisResumoResponse from(LoteRecebiveisResumo resumo) {
        return new LoteRecebiveisResumoResponse(resumo.id(), resumo.dataReferencia(), resumo.status(), resumo.createdAt());
    }
}
