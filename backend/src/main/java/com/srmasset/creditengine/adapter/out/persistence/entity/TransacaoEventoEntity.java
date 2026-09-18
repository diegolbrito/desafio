package com.srmasset.creditengine.adapter.out.persistence.entity;

import com.srmasset.creditengine.domain.TipoEventoTransacao;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Sem @Version/@UpdateTimestamp: a tabela e' append-only (ver migration), nada
 * nesta entidade e' atualizado apos o insert.
 */
@Entity
@Table(name = "transacao_evento")
public class TransacaoEventoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "lote_recebivel_id", nullable = false)
    private UUID loteRecebivelId;

    @Column(name = "recebivel_id")
    private UUID recebivelId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 30)
    private TipoEventoTransacao tipo;

    @Column(name = "descricao", nullable = false, length = 1000)
    private String descricao;

    @Column(name = "ocorrido_em", nullable = false)
    private OffsetDateTime ocorridoEm;

    protected TransacaoEventoEntity() {
    }

    public TransacaoEventoEntity(UUID loteRecebivelId, UUID recebivelId, TipoEventoTransacao tipo,
                                  String descricao, OffsetDateTime ocorridoEm) {
        this.loteRecebivelId = loteRecebivelId;
        this.recebivelId = recebivelId;
        this.tipo = tipo;
        this.descricao = descricao;
        this.ocorridoEm = ocorridoEm;
    }

    public UUID getId() {
        return id;
    }

    public UUID getLoteRecebivelId() {
        return loteRecebivelId;
    }

    public UUID getRecebivelId() {
        return recebivelId;
    }

    public TipoEventoTransacao getTipo() {
        return tipo;
    }

    public String getDescricao() {
        return descricao;
    }

    public OffsetDateTime getOcorridoEm() {
        return ocorridoEm;
    }
}
