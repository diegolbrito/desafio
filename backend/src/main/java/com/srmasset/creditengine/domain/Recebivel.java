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
 *
 * <p>O ativo e' sempre denominado em BRL (ver SPEC.md, "Premissas adotadas" item 3) -
 * {@code moeda} nao e' escolhida por quem cria o recebivel, so' {@code moedaPagamento}.
 */
public class Recebivel {

    private UUID id;
    private final String ativo;
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

    private Recebivel(String ativo, BigDecimal valorBruto, LocalDate dataVencimento,
                       CategoriaRisco categoriaRisco, Moeda moedaPagamento) {
        this.ativo = ativo;
        this.valorBruto = valorBruto;
        this.moeda = Moeda.BRL;
        this.dataVencimento = dataVencimento;
        this.categoriaRisco = categoriaRisco;
        this.moedaPagamento = moedaPagamento;
        this.status = StatusRecebivel.PENDENTE;
    }

    public static Recebivel criar(String ativo, BigDecimal valorBruto,
                                   LocalDate dataVencimento, CategoriaRisco categoriaRisco) {
        return criar(ativo, valorBruto, dataVencimento, categoriaRisco, Moeda.BRL);
    }

    /**
     * @param moedaPagamento moeda em que o recebivel e' efetivamente pago; se {@code null},
     *                       assume BRL (sem cross-currency). O ativo em si e' sempre denominado
     *                       em BRL - ver SPEC.md "Premissas adotadas" item 3 - por isso quem
     *                       cria o recebivel so' escolhe a moeda de pagamento, nunca a moeda do
     *                       ativo. A cotacao de cambio usada na conversao (quando moedaPagamento
     *                       difere de BRL) nao e' informada aqui - vem de configuracao da
     *                       aplicacao (mesmo padrao de custoOperacional) e e' aplicada/
     *                       snapshotada em {@link #aplicarPrecificacao}.
     */
    public static Recebivel criar(String ativo, BigDecimal valorBruto,
                                   LocalDate dataVencimento, CategoriaRisco categoriaRisco,
                                   Moeda moedaPagamento) {
        if (ativo == null || ativo.isBlank()) {
            throw new RecebivelInvalidoException("Ativo e obrigatorio");
        }
        if (valorBruto == null || valorBruto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new RecebivelInvalidoException("Valor bruto deve ser positivo");
        }
        if (dataVencimento == null) {
            throw new RecebivelInvalidoException("Data de vencimento e obrigatoria");
        }
        if (categoriaRisco == null) {
            throw new RecebivelInvalidoException("Categoria de risco e obrigatoria");
        }
        Moeda moedaPagamentoResolvida = moedaPagamento == null ? Moeda.BRL : moedaPagamento;
        return new Recebivel(ativo, valorBruto, dataVencimento, categoriaRisco, moedaPagamentoResolvida);
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
