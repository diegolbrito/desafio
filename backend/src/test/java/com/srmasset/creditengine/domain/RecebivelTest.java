package com.srmasset.creditengine.domain;

import com.srmasset.creditengine.domain.exception.LiquidacaoInvalidaException;
import com.srmasset.creditengine.domain.exception.PrazoInvalidoException;
import com.srmasset.creditengine.domain.exception.RecebivelInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecebivelTest {

    private Recebivel recebivelValido() {
        return Recebivel.criar("Duplicata Mercantil", new BigDecimal("1000.00"),
                LocalDate.of(2026, 12, 31), CategoriaRisco.B);
    }

    @Test
    void criaRecebivelValidoComStatusPendente() {
        Recebivel recebivel = recebivelValido();

        assertThat(recebivel.getStatus()).isEqualTo(StatusRecebivel.PENDENTE);
        assertThat(recebivel.getAtivo()).isEqualTo("Duplicata Mercantil");
    }

    @Test
    void ativoEhSempreDenominadoEmBrl() {
        Recebivel recebivel = Recebivel.criar("Ativo", new BigDecimal("1000.00"),
                LocalDate.of(2026, 12, 31), CategoriaRisco.B, Moeda.USD);

        assertThat(recebivel.getMoeda()).isEqualTo(Moeda.BRL);
    }

    @Test
    void semMoedaPagamentoInformadaAssumeBrlSemCotacao() {
        Recebivel recebivel = recebivelValido();

        assertThat(recebivel.getMoedaPagamento()).isEqualTo(Moeda.BRL);
        assertThat(recebivel.getCotacaoCambio()).isNull();
    }

    @Test
    void aceitaMoedaPagamentoDiferenteDeBrl() {
        Recebivel recebivel = Recebivel.criar("Ativo", new BigDecimal("1000.00"),
                LocalDate.of(2026, 12, 31), CategoriaRisco.B, Moeda.USD);

        assertThat(recebivel.getMoedaPagamento()).isEqualTo(Moeda.USD);
        // cotacaoCambio so e' preenchida na precificacao (vem de configuracao, nao da criacao)
        assertThat(recebivel.getCotacaoCambio()).isNull();
    }

    @Test
    void rejeitaValorBrutoNaoPositivo() {
        assertThatThrownBy(() -> Recebivel.criar("Ativo", new BigDecimal("0.00"),
                LocalDate.of(2026, 12, 31), CategoriaRisco.A))
                .isInstanceOf(RecebivelInvalidoException.class);
    }

    @Test
    void rejeitaAtivoEmBranco() {
        assertThatThrownBy(() -> Recebivel.criar("  ", new BigDecimal("100.00"),
                LocalDate.of(2026, 12, 31), CategoriaRisco.A))
                .isInstanceOf(RecebivelInvalidoException.class);
    }

    @Test
    void calculaPrazoMesesCorretamenteQuandoExatoEmMesesInteiros() {
        Recebivel recebivel = Recebivel.criar("Ativo", new BigDecimal("100.00"),
                LocalDate.of(2026, 10, 20), CategoriaRisco.A);

        long prazo = recebivel.calcularPrazoMeses(LocalDate.of(2026, 9, 20));

        assertThat(prazo).isEqualTo(1);
    }

    @Test
    void arredondaMesIncompletoParaCimaComoMesInteiro() {
        Recebivel recebivel = Recebivel.criar("Ativo", new BigDecimal("100.00"),
                LocalDate.of(2026, 11, 5), CategoriaRisco.A);

        // 1 mes completo (20/09 -> 20/10) + fracao de mes (20/10 -> 05/11) = arredonda para 2
        long prazo = recebivel.calcularPrazoMeses(LocalDate.of(2026, 9, 20));

        assertThat(prazo).isEqualTo(2);
    }

    @Test
    void rejeitaPrazoQuandoVencimentoNaoEhPosteriorADataReferencia() {
        Recebivel recebivel = Recebivel.criar("Ativo", new BigDecimal("100.00"),
                LocalDate.of(2026, 9, 20), CategoriaRisco.A);

        assertThatThrownBy(() -> recebivel.calcularPrazoMeses(LocalDate.of(2026, 9, 20)))
                .isInstanceOf(PrazoInvalidoException.class);
        assertThatThrownBy(() -> recebivel.calcularPrazoMeses(LocalDate.of(2026, 9, 21)))
                .isInstanceOf(PrazoInvalidoException.class);
    }

    @Test
    void aplicarPrecificacaoTransicionaParaPrecificadoEPreencheValores() {
        Recebivel recebivel = recebivelValido();
        ResultadoDesagio resultado = new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000"));

        recebivel.aplicarPrecificacao(resultado);

        assertThat(recebivel.getStatus()).isEqualTo(StatusRecebivel.PRECIFICADO);
        assertThat(recebivel.getValorPresente()).isEqualByComparingTo("950.00");
        assertThat(recebivel.getValorDesagio()).isEqualByComparingTo("50.00");
        assertThat(recebivel.getTaxaDescontoAplicada()).isEqualByComparingTo("0.105000");
        assertThat(recebivel.getCotacaoCambio()).isNull();
    }

    @Test
    void aplicarPrecificacaoComCotacaoSnapshotaACotacaoAplicada() {
        Recebivel recebivel = Recebivel.criar("Ativo", new BigDecimal("1000.00"),
                LocalDate.of(2026, 12, 31), CategoriaRisco.B, Moeda.USD);
        ResultadoDesagio resultado = new ResultadoDesagio(
                new BigDecimal("190.00"), new BigDecimal("10.00"), new BigDecimal("0.105000"));

        recebivel.aplicarPrecificacao(resultado, new BigDecimal("5.20"));

        assertThat(recebivel.getCotacaoCambio()).isEqualByComparingTo("5.20");
    }

    @Test
    void rejeitarTransicionaParaRejeitadoComMotivo() {
        Recebivel recebivel = recebivelValido();

        recebivel.rejeitar("Data de vencimento invalida");

        assertThat(recebivel.getStatus()).isEqualTo(StatusRecebivel.REJEITADO);
        assertThat(recebivel.getMotivoRejeicao()).isEqualTo("Data de vencimento invalida");
    }

    @Test
    void naoPermitePrecificarDuasVezes() {
        Recebivel recebivel = recebivelValido();
        ResultadoDesagio resultado = new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000"));
        recebivel.aplicarPrecificacao(resultado);

        assertThatThrownBy(() -> recebivel.aplicarPrecificacao(resultado))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void liquidarTransicionaDePrecificadoParaLiquidadoESnapshotaOInstante() {
        Recebivel recebivel = recebivelValido();
        recebivel.aplicarPrecificacao(new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000")));
        OffsetDateTime agora = OffsetDateTime.ofInstant(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);

        boolean liquidadoAgora = recebivel.liquidar(agora);

        assertThat(liquidadoAgora).isTrue();
        assertThat(recebivel.getStatus()).isEqualTo(StatusRecebivel.LIQUIDADO);
        assertThat(recebivel.getLiquidadoEm()).isEqualTo(agora);
    }

    @Test
    void liquidarEhIdempotenteQuandoJaLiquidado() {
        Recebivel recebivel = recebivelValido();
        recebivel.aplicarPrecificacao(new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000")));
        OffsetDateTime primeiraLiquidacao = OffsetDateTime.ofInstant(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        recebivel.liquidar(primeiraLiquidacao);

        OffsetDateTime segundaTentativa = OffsetDateTime.ofInstant(Instant.parse("2026-09-21T08:00:00Z"), ZoneOffset.UTC);
        boolean liquidadoNaSegundaChamada = recebivel.liquidar(segundaTentativa);

        assertThat(liquidadoNaSegundaChamada).isFalse();
        assertThat(recebivel.getStatus()).isEqualTo(StatusRecebivel.LIQUIDADO);
        // repetir a chamada nao pode sobrescrever o instante da liquidacao original
        assertThat(recebivel.getLiquidadoEm()).isEqualTo(primeiraLiquidacao);
    }

    @Test
    void naoPermiteLiquidarRecebivelPendente() {
        Recebivel recebivel = recebivelValido();

        assertThatThrownBy(() -> recebivel.liquidar(OffsetDateTime.now()))
                .isInstanceOf(LiquidacaoInvalidaException.class);
    }

    @Test
    void naoPermiteLiquidarRecebivelRejeitado() {
        Recebivel recebivel = recebivelValido();
        recebivel.rejeitar("Data de vencimento invalida");

        assertThatThrownBy(() -> recebivel.liquidar(OffsetDateTime.now()))
                .isInstanceOf(LiquidacaoInvalidaException.class);
    }
}
