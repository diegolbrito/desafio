package com.srmasset.creditengine.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ConversorCambialTest {

    private final ConversorCambial conversor = new ConversorCambial();

    @Test
    void naoConverteQuandoMoedaPagamentoEIgualAMoedaTitulo() {
        ResultadoDesagio resultado = new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000"));

        ResultadoDesagio convertido = conversor.converter(resultado, new BigDecimal("1000.00"),
                Moeda.BRL, Moeda.BRL, null);

        assertThat(convertido).isSameAs(resultado);
    }

    @Test
    void convertePresenteEDesagioDeBrlParaUsdDividindoPelaCotacao() {
        ResultadoDesagio resultado = new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000"));

        ResultadoDesagio convertido = conversor.converter(resultado, new BigDecimal("1000.00"),
                Moeda.BRL, Moeda.USD, new BigDecimal("5.00"));

        // 1000/5 = 200.00 (valorBruto convertido); 950/5 = 190.00 (valorPresente convertido)
        assertThat(convertido.valorPresente()).isEqualByComparingTo("190.00");
        assertThat(convertido.valorDesagio()).isEqualByComparingTo("10.00");
        assertThat(convertido.taxaDescontoAplicada()).isEqualByComparingTo("0.105000");
    }

    @Test
    void convertePresenteEDesagioDeUsdParaBrlMultiplicandoPelaCotacao() {
        ResultadoDesagio resultado = new ResultadoDesagio(
                new BigDecimal("190.00"), new BigDecimal("10.00"), new BigDecimal("0.073000"));

        ResultadoDesagio convertido = conversor.converter(resultado, new BigDecimal("200.00"),
                Moeda.USD, Moeda.BRL, new BigDecimal("5.00"));

        assertThat(convertido.valorPresente()).isEqualByComparingTo("950.00");
        assertThat(convertido.valorDesagio()).isEqualByComparingTo("50.00");
    }

    @Test
    void preservaInvarianteDeReconciliacaoAposConversaoComArredondamento() {
        ResultadoDesagio resultado = new ResultadoDesagio(
                new BigDecimal("950.33"), new BigDecimal("49.67"), new BigDecimal("0.105000"));

        ResultadoDesagio convertido = conversor.converter(resultado, new BigDecimal("1000.00"),
                Moeda.BRL, Moeda.USD, new BigDecimal("5.234567"));

        assertThat(convertido.valorPresente().add(convertido.valorDesagio()))
                .isEqualByComparingTo(new BigDecimal("1000.00").divide(new BigDecimal("5.234567"), 2, java.math.RoundingMode.HALF_EVEN));
    }
}
