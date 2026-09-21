package com.srmasset.creditengine.adapter.out.persistence.entity;

import com.srmasset.creditengine.domain.StatusLote;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * {@code @SQLRestriction} aplica "deleted_at is null" em toda consulta gerada
 * pelo Hibernate para esta entidade (findById, findAll, JPQL) - ver SPEC.md
 * "Banco de dados": nada e' deletado fisicamente, soft delete via deleted_at.
 * Sem isso, um registro marcado como deletado continuaria aparecendo
 * normalmente em qualquer consulta.
 */
@Entity
@Table(name = "lote_recebivel")
@SQLRestriction("deleted_at is null")
public class LoteRecebivelEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @Column(name = "data_referencia", nullable = false)
    private LocalDate dataReferencia;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusLote status;

    @OneToMany(mappedBy = "loteRecebivel", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RecebivelEntity> recebiveis = new ArrayList<>();

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

    protected LoteRecebivelEntity() {
    }

    public LoteRecebivelEntity(LocalDate dataReferencia, StatusLote status) {
        this.dataReferencia = dataReferencia;
        this.status = status;
    }

    public void adicionarRecebivel(RecebivelEntity recebivel) {
        recebivel.atribuirLoteRecebivel(this);
        this.recebiveis.add(recebivel);
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

    public List<RecebivelEntity> getRecebiveis() {
        return recebiveis;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
