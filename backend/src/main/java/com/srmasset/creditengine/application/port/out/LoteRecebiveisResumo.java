package com.srmasset.creditengine.application.port.out;

import com.srmasset.creditengine.domain.StatusLote;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Projecao leve para a listagem paginada (sem os recebiveis do lote, para evitar
 * N+1 - ver SPEC.md, "Decisões de precisão numérica"/critérios de desempenho).
 * O detalhe completo, com os recebiveis, fica em {@link LoteRecebiveisDetalhe}.
 */
public record LoteRecebiveisResumo(UUID id, LocalDate dataReferencia, StatusLote status, OffsetDateTime createdAt) {
}
