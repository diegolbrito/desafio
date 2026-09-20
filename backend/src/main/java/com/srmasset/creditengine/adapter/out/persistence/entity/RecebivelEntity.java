package com.srmasset.creditengine.adapter.out.persistence.entity;

import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.Moeda;
import com.srmasset.creditengine.domain.StatusRecebivel;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "recebivel")
public class RecebivelEntity {

    @Id
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lote_recebivel_id", nullable = false)
    private LoteRecebivelEntity loteRecebivel;

    @Column(name = "ativo", nullable = false, length = 255)
    private String ativo;

    @Column(name = "valor_bruto", nullable = false)
    private BigDecimal valorBruto;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "moeda", nullable = false, columnDefinition = "char(3)")
    private Moeda moeda;

    @Column(name = "data_vencimento", nullable = false)
    private LocalDate dataVencimento;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria_risco", nullable = false, length = 2)
    private CategoriaRisco categoriaRisco;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private StatusRecebivel status;

    @Column(name = "valor_presente")
    private BigDecimal valorPresente;

    @Column(name = "valor_desagio")
    private BigDecimal valorDesagio;

    @Column(name = "taxa_desconto_aplicada")
    private BigDecimal taxaDescontoAplicada;

    @Column(name = "motivo_rejeicao", length = 500)
    private String motivoRejeicao;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "moeda_pagamento", nullable = false, columnDefinition = "char(3)")
    private Moeda moedaPagamento;

    @Column(name = "cotacao_cambio")
    private BigDecimal cotacaoCambio;

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

    protected RecebivelEntity() {
    }

    public RecebivelEntity(String ativo, BigDecimal valorBruto, Moeda moeda, LocalDate dataVencimento,
                            CategoriaRisco categoriaRisco, StatusRecebivel status,
                            BigDecimal valorPresente, BigDecimal valorDesagio,
                            BigDecimal taxaDescontoAplicada, String motivoRejeicao,
                            Moeda moedaPagamento, BigDecimal cotacaoCambio) {
        this.ativo = ativo;
        this.valorBruto = valorBruto;
        this.moeda = moeda;
        this.dataVencimento = dataVencimento;
        this.categoriaRisco = categoriaRisco;
        this.status = status;
        this.valorPresente = valorPresente;
        this.valorDesagio = valorDesagio;
        this.taxaDescontoAplicada = taxaDescontoAplicada;
        this.motivoRejeicao = motivoRejeicao;
        this.moedaPagamento = moedaPagamento;
        this.cotacaoCambio = cotacaoCambio;
    }

    void atribuirLoteRecebivel(LoteRecebivelEntity loteRecebivel) {
        this.loteRecebivel = loteRecebivel;
    }

    public UUID getId() {
        return id;
    }

    public String getAtivo() {
        return ativo;
    }

    public BigDecimal getValorBruto() {
        return valorBruto;
    }

    public Moeda getMoeda() {
        return moeda;
    }

    public LocalDate getDataVencimento() {
        return dataVencimento;
    }

    public CategoriaRisco getCategoriaRisco() {
        return categoriaRisco;
    }

    public StatusRecebivel getStatus() {
        return status;
    }

    public BigDecimal getValorPresente() {
        return valorPresente;
    }

    public BigDecimal getValorDesagio() {
        return valorDesagio;
    }

    public BigDecimal getTaxaDescontoAplicada() {
        return taxaDescontoAplicada;
    }

    public String getMotivoRejeicao() {
        return motivoRejeicao;
    }

    public Moeda getMoedaPagamento() {
        return moedaPagamento;
    }

    public BigDecimal getCotacaoCambio() {
        return cotacaoCambio;
    }
}
