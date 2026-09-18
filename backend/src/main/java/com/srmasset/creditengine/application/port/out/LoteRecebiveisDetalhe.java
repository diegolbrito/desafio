package com.srmasset.creditengine.application.port.out;

import com.srmasset.creditengine.domain.StatusLote;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Projecao completa (com recebiveis) usada pelo GET de detalhe de um lote. */
public record LoteRecebiveisDetalhe(UUID id, LocalDate dataReferencia, StatusLote status,
                                     OffsetDateTime createdAt, List<RecebivelLeitura> recebiveis) {
}
