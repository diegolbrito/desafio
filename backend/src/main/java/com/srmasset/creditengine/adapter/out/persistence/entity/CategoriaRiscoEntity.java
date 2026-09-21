package com.srmasset.creditengine.adapter.out.persistence.entity;

import com.srmasset.creditengine.domain.CategoriaRisco;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Tabela de referencia (seed via migration); somente leitura pela aplicacao.
 * {@code @SQLRestriction} aplica "deleted_at is null" em toda consulta gerada
 * pelo Hibernate para esta entidade - ver SPEC.md "Banco de dados".
 */
@Entity
@Table(name = "categoria_risco")
@SQLRestriction("deleted_at is null")
public class CategoriaRiscoEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "codigo", nullable = false, length = 2)
    private CategoriaRisco codigo;

    @Column(name = "spread_risco", nullable = false)
    private BigDecimal spreadRisco;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    protected CategoriaRiscoEntity() {
    }

    public UUID getId() {
        return id;
    }

    public CategoriaRisco getCodigo() {
        return codigo;
    }

    public BigDecimal getSpreadRisco() {
        return spreadRisco;
    }

    public OffsetDateTime getDeletedAt() {
        return deletedAt;
    }

    /** Soft delete (ver SPEC.md "Banco de dados"): nada e' deletado fisicamente. */
    public void marcarComoDeletado(OffsetDateTime agora) {
        this.deletedAt = agora;
    }
}
