package com.srmasset.creditengine.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ConversorCambialTest {

    private final ConversorCambial conversor = new ConversorCambial();

    @Test
    void naoConverteQuandoMoedaPagamentoEBrl() {
        ResultadoDesagio resultado = new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000"));

        ResultadoDesagio convertido = conversor.converter(resultado, Moeda.BRL, null);

        assertThat(convertido).isSameAs(resultado);
    }

    @Test
    void convertePresenteDeBrlParaUsdDividindoPelaCotacaoEMantemDesagioEmBrl() {
        ResultadoDesagio resultado = new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000"));

        ResultadoDesagio convertido = conversor.converter(resultado, Moeda.USD, new BigDecimal("5.00"));

        // 950/5 = 190.00 (valorPresente convertido); deságio NAO e' convertido, fica em BRL
        assertThat(convertido.valorPresente()).isEqualByComparingTo("190.00");
        assertThat(convertido.valorDesagio()).isEqualByComparingTo("50.00");
        assertThat(convertido.taxaDescontoAplicada()).isEqualByComparingTo("0.105000");
    }

    @Test
    void valorDesagioNuncaMudaComAConversao() {
        ResultadoDesagio resultado = new ResultadoDesagio(
                new BigDecimal("950.33"), new BigDecimal("49.67"), new BigDecimal("0.105000"));

        ResultadoDesagio convertido = conversor.converter(resultado, Moeda.USD, new BigDecimal("5.234567"));

        assertThat(convertido.valorDesagio()).isEqualByComparingTo(resultado.valorDesagio());
    }
}
