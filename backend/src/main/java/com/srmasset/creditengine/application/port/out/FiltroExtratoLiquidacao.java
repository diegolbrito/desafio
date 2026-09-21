package com.srmasset.creditengine.application.port.out;

import com.srmasset.creditengine.domain.Moeda;

import java.time.OffsetDateTime;

/** Todos os campos sao opcionais (null = filtro nao aplicado). */
public record FiltroExtratoLiquidacao(OffsetDateTime dataInicio, OffsetDateTime dataFim,
                                       String ativo, Moeda moeda) {
}
