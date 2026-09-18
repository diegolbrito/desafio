package com.srmasset.creditengine.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Fato auditavel append-only sobre um lote/recebivel (ver SPEC.md, convencao
 * "transacao_evento": tabelas de evento sao append-only, sem UPDATE/DELETE).
 */
public record EventoTransacao(TipoEventoTransacao tipo, UUID loteId, UUID recebivelId,
                               String descricao, OffsetDateTime ocorridoEm) {

    public static EventoTransacao loteRecebido(UUID loteId, OffsetDateTime agora) {
        return new EventoTransacao(TipoEventoTransacao.LOTE_RECEBIDO, loteId, null,
                "Lote recebido para precificacao", agora);
    }

    public static EventoTransacao lotePrecificado(UUID loteId, OffsetDateTime agora) {
        return new EventoTransacao(TipoEventoTransacao.LOTE_PRECIFICADO, loteId, null,
                "Lote precificado com sucesso", agora);
    }

    public static EventoTransacao loteComErro(UUID loteId, String motivo, OffsetDateTime agora) {
        return new EventoTransacao(TipoEventoTransacao.LOTE_ERRO, loteId, null,
                "Falha ao precificar lote: " + motivo, agora);
    }

    public static EventoTransacao recebivelPrecificado(UUID loteId, Recebivel recebivel, OffsetDateTime agora) {
        return new EventoTransacao(TipoEventoTransacao.RECEBIVEL_PRECIFICADO, loteId, recebivel.getId(),
                "Recebivel precificado: valorPresente=%s, valorDesagio=%s, taxaDescontoAplicada=%s".formatted(
                        recebivel.getValorPresente(), recebivel.getValorDesagio(), recebivel.getTaxaDescontoAplicada()),
                agora);
    }

    public static EventoTransacao recebivelRejeitado(UUID loteId, Recebivel recebivel, OffsetDateTime agora) {
        return new EventoTransacao(TipoEventoTransacao.RECEBIVEL_REJEITADO, loteId, recebivel.getId(),
                "Recebivel rejeitado: " + recebivel.getMotivoRejeicao(), agora);
    }
}
