package com.srmasset.creditengine.domain;

import com.srmasset.creditengine.domain.exception.LoteRecebiveisInvalidoException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Agregado raiz: um lote recebido para precificacao, com seus recebiveis.
 * Nao ha etapa de aprovacao (ver SPEC.md, "Premissas adotadas" - item 5): o lote
 * nasce RECEBIDO e e' precificado automaticamente pelo caso de uso de aplicacao.
 */
public class LoteRecebiveis {

    private UUID id;
    private final LocalDate dataReferencia;
    private StatusLote status;
    private final List<Recebivel> recebiveis;

    private LoteRecebiveis(LocalDate dataReferencia, List<Recebivel> recebiveis) {
        this.dataReferencia = dataReferencia;
        this.recebiveis = new ArrayList<>(recebiveis);
        this.status = StatusLote.RECEBIDO;
    }

    public static LoteRecebiveis criar(LocalDate dataReferencia, List<Recebivel> recebiveis) {
        if (dataReferencia == null) {
            throw new LoteRecebiveisInvalidoException("Data de referencia do lote e obrigatoria");
        }
        if (recebiveis == null || recebiveis.isEmpty()) {
            throw new LoteRecebiveisInvalidoException("Lote deve conter ao menos um recebivel");
        }
        return new LoteRecebiveis(dataReferencia, recebiveis);
    }

    public void marcarPrecificado() {
        this.status = StatusLote.PRECIFICADO;
    }

    public void marcarErro() {
        this.status = StatusLote.ERRO;
    }

    public void atribuirId(UUID id) {
        this.id = id;
    }

    public UUID getId() {
        return id;
    }

    public LocalDate getDataReferencia() {
        return dataReferencia;
    }

    public StatusLote getStatus() {
        return status;
    }

    public List<Recebivel> getRecebiveis() {
        return Collections.unmodifiableList(recebiveis);
    }
}
