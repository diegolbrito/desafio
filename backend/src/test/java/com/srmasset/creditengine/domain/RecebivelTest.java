package com.srmasset.creditengine.domain;

import com.srmasset.creditengine.domain.exception.PrazoInvalidoException;
import com.srmasset.creditengine.domain.exception.RecebivelInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecebivelTest {

    private Recebivel recebivelValido() {
        return Recebivel.criar("Cedente Ltda", new BigDecimal("1000.00"), Moeda.BRL,
                LocalDate.of(2026, 12, 31), CategoriaRisco.B);
    }

    @Test
    void criaRecebivelValidoComStatusPendente() {
        Recebivel recebivel = recebivelValido();

        assertThat(recebivel.getStatus()).isEqualTo(StatusRecebivel.PENDENTE);
        assertThat(recebivel.getCedente()).isEqualTo("Cedente Ltda");
    }

    @Test
    void rejeitaValorBrutoNaoPositivo() {
        assertThatThrownBy(() -> Recebivel.criar("Cedente", new BigDecimal("0.00"), Moeda.BRL,
                LocalDate.of(2026, 12, 31), CategoriaRisco.A))
                .isInstanceOf(RecebivelInvalidoException.class);
    }

    @Test
    void rejeitaCedenteEmBranco() {
        assertThatThrownBy(() -> Recebivel.criar("  ", new BigDecimal("100.00"), Moeda.BRL,
                LocalDate.of(2026, 12, 31), CategoriaRisco.A))
                .isInstanceOf(RecebivelInvalidoException.class);
    }

    @Test
    void calculaPrazoMesesCorretamenteQuandoExatoEmMesesInteiros() {
        Recebivel recebivel = Recebivel.criar("Cedente", new BigDecimal("100.00"), Moeda.BRL,
                LocalDate.of(2026, 10, 20), CategoriaRisco.A);

        long prazo = recebivel.calcularPrazoMeses(LocalDate.of(2026, 9, 20));

        assertThat(prazo).isEqualTo(1);
    }

    @Test
    void arredondaMesIncompletoParaCimaComoMesInteiro() {
        Recebivel recebivel = Recebivel.criar("Cedente", new BigDecimal("100.00"), Moeda.BRL,
                LocalDate.of(2026, 11, 5), CategoriaRisco.A);

        // 1 mes completo (20/09 -> 20/10) + fracao de mes (20/10 -> 05/11) = arredonda para 2
        long prazo = recebivel.calcularPrazoMeses(LocalDate.of(2026, 9, 20));

        assertThat(prazo).isEqualTo(2);
    }

    @Test
    void rejeitaPrazoQuandoVencimentoNaoEhPosteriorADataReferencia() {
        Recebivel recebivel = Recebivel.criar("Cedente", new BigDecimal("100.00"), Moeda.BRL,
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
}
