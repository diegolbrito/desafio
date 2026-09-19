package com.srmasset.creditengine.domain;

import com.srmasset.creditengine.domain.exception.PrazoInvalidoException;
import com.srmasset.creditengine.domain.exception.RecebivelInvalidoException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/**
 * Um item do lote de recebiveis. Nasce PENDENTE e transita para PRECIFICADO ou
 * REJEITADO apos o processamento (ver LoteRecebiveis / caso de uso de precificacao).
 */
public class Recebivel {

    private UUID id;
    private final String cedente;
    private final BigDecimal valorBruto;
    private final Moeda moeda;
    private final LocalDate dataVencimento;
    private final CategoriaRisco categoriaRisco;
    private final Moeda moedaPagamento;

    private StatusRecebivel status;
    private BigDecimal valorPresente;
    private BigDecimal valorDesagio;
    private BigDecimal taxaDescontoAplicada;
    private BigDecimal cotacaoCambio;
    private String motivoRejeicao;

    private Recebivel(String cedente, BigDecimal valorBruto, Moeda moeda, LocalDate dataVencimento,
                       CategoriaRisco categoriaRisco, Moeda moedaPagamento) {
        this.cedente = cedente;
        this.valorBruto = valorBruto;
        this.moeda = moeda;
        this.dataVencimento = dataVencimento;
        this.categoriaRisco = categoriaRisco;
        this.moedaPagamento = moedaPagamento;
        this.status = StatusRecebivel.PENDENTE;
    }

    public static Recebivel criar(String cedente, BigDecimal valorBruto, Moeda moeda,
                                   LocalDate dataVencimento, CategoriaRisco categoriaRisco) {
        return criar(cedente, valorBruto, moeda, dataVencimento, categoriaRisco, moeda);
    }

    /**
     * @param moedaPagamento moeda em que o recebivel e' efetivamente pago; se {@code null},
     *                       assume a propria {@code moeda} do titulo (sem cross-currency). A
     *                       cotacao de cambio usada na conversao (quando as moedas diferem) nao
     *                       e' informada aqui - vem de configuracao da aplicacao (mesmo padrao
     *                       de custoOperacional, ver SPEC.md "Premissas adotadas" item 3) e e'
     *                       aplicada/snapshotada em {@link #aplicarPrecificacao}.
     */
    public static Recebivel criar(String cedente, BigDecimal valorBruto, Moeda moeda,
                                   LocalDate dataVencimento, CategoriaRisco categoriaRisco,
                                   Moeda moedaPagamento) {
        if (cedente == null || cedente.isBlank()) {
            throw new RecebivelInvalidoException("Cedente e obrigatorio");
        }
        if (valorBruto == null || valorBruto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RecebivelInvalidoException("Valor bruto deve ser positivo");
        }
        if (moeda == null) {
            throw new RecebivelInvalidoException("Moeda e obrigatoria");
        }
        if (dataVencimento == null) {
            throw new RecebivelInvalidoException("Data de vencimento e obrigatoria");
        }
        if (categoriaRisco == null) {
            throw new RecebivelInvalidoException("Categoria de risco e obrigatoria");
        }
        Moeda moedaPagamentoResolvida = moedaPagamento == null ? moeda : moedaPagamento;
        return new Recebivel(cedente, valorBruto, moeda, dataVencimento, categoriaRisco, moedaPagamentoResolvida);
    }

    /**
     * Prazo em meses inteiros entre a data de referencia da precificacao e o vencimento,
     * usado como expoente dos juros compostos mensais (ver SPEC.md, "Premissas adotadas"
     * item 1). Meses incompletos contam como mes inteiro (arredondamento para cima) - o
     * mes iniciado e' cobrado por inteiro, convencao usual em desconto de recebiveis.
     * Rejeita (via excecao) recebiveis cujo vencimento nao seja estritamente posterior
     * a data de referencia.
     */
    public long calcularPrazoMeses(LocalDate dataReferencia) {
        long mesesCompletos = ChronoUnit.MONTHS.between(dataReferencia, dataVencimento);
        LocalDate dataAposMesesCompletos = dataReferencia.plusMonths(mesesCompletos);
        long prazoMeses = dataAposMesesCompletos.isBefore(dataVencimento) ? mesesCompletos + 1 : mesesCompletos;
        if (prazoMeses <= 0) {
            throw new PrazoInvalidoException(
                    "Data de vencimento (%s) deve ser posterior a data de referencia (%s)"
                            .formatted(dataVencimento, dataReferencia));
        }
        return prazoMeses;
    }

    public void aplicarPrecificacao(ResultadoDesagio resultado) {
        aplicarPrecificacao(resultado, null);
    }

    /**
     * @param cotacaoCambioAplicada cotacao de cambio efetivamente usada para converter o
     *                              resultado para moedaPagamento (snapshot de auditoria - ver
     *                              SPEC.md "Premissas adotadas" item 3); {@code null} quando
     *                              moedaPagamento == moeda (sem conversao).
     */
    public void aplicarPrecificacao(ResultadoDesagio resultado, BigDecimal cotacaoCambioAplicada) {
        exigirStatus(StatusRecebivel.PENDENTE);
        this.valorPresente = resultado.valorPresente();
        this.valorDesagio = resultado.valorDesagio();
        this.taxaDescontoAplicada = resultado.taxaDescontoAplicada();
        this.cotacaoCambio = cotacaoCambioAplicada;
        this.status = StatusRecebivel.PRECIFICADO;
    }

    public void rejeitar(String motivo) {
        exigirStatus(StatusRecebivel.PENDENTE);
        this.motivoRejeicao = motivo;
        this.status = StatusRecebivel.REJEITADO;
    }

    public void atribuirId(UUID id) {
        this.id = id;
    }

    private void exigirStatus(StatusRecebivel esperado) {
        if (this.status != esperado) {
            throw new IllegalStateException(
                    "Recebivel esta em status %s, esperado %s".formatted(status, esperado));
        }
    }

    public UUID getId() {
        return id;
    }

    public String getCedente() {
        return cedente;
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

    public Moeda getMoedaPagamento() {
        return moedaPagamento;
    }

    public BigDecimal getCotacaoCambio() {
        return cotacaoCambio;
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
}
