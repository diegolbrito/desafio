package com.srmasset.creditengine.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraDesagioTest {

    private final CalculadoraDesagio calculadora = new CalculadoraDesagio();

    @Test
    void quandoPrazoEhUmMes_fatorDescontoEhExatamenteUmMaisTaxa() {
        // Com prazoMeses == 1, o expoente e' 1, tornando o resultado verificavel
        // manualmente: valorPresente = valorBruto / (1 + taxa).
        BigDecimal valorBruto = new BigDecimal("10000.00");
        BigDecimal taxaBase = new BigDecimal("0.05");
        BigDecimal spreadRisco = new BigDecimal("0.03");
        BigDecimal custoOperacional = new BigDecimal("0.02");
        // taxaDesconto total = 0.10

        ResultadoDesagio resultado = calculadora.calcular(
                valorBruto, 1, taxaBase, spreadRisco, custoOperacional);

        BigDecimal valorPresenteEsperado = valorBruto.divide(new BigDecimal("1.10"), 2, java.math.RoundingMode.HALF_EVEN);
        assertThat(resultado.valorPresente()).isEqualByComparingTo(valorPresenteEsperado);
        assertThat(resultado.valorDesagio()).isEqualByComparingTo(valorBruto.subtract(valorPresenteEsperado));
        assertThat(resultado.taxaDescontoAplicada()).isEqualByComparingTo("0.100000");
    }

    @Test
    void quandoPrazoEhDoisMeses_fatorDescontoEhTaxaAoQuadrado() {
        BigDecimal valorBruto = new BigDecimal("5000.00");
        BigDecimal taxaBase = new BigDecimal("0.048");
        BigDecimal spreadRisco = new BigDecimal("0.02");
        BigDecimal custoOperacional = new BigDecimal("0.005");
        // taxaDesconto total = 0.073, capitalizada por 2 meses: (1.073)^2

        ResultadoDesagio resultado = calculadora.calcular(
                valorBruto, 2, taxaBase, spreadRisco, custoOperacional);

        BigDecimal fatorEsperado = new BigDecimal("1.073").multiply(new BigDecimal("1.073"));
        BigDecimal valorPresenteEsperado = valorBruto.divide(fatorEsperado, 2, java.math.RoundingMode.HALF_EVEN);
        assertThat(resultado.valorPresente()).isEqualByComparingTo(valorPresenteEsperado);
    }

    @Test
    void valorPresenteMaisDesagioSempreReconstituiValorBruto() {
        BigDecimal valorBruto = new BigDecimal("12345.67");

        ResultadoDesagio resultado = calculadora.calcular(
                valorBruto, 3, new BigDecimal("0.008469"),
                new BigDecimal("0.002871"), new BigDecimal("0.000416"));

        assertThat(resultado.valorPresente().add(resultado.valorDesagio()))
                .isEqualByComparingTo(valorBruto);
    }

    @Test
    void prazoMaiorGeraDesagioMaior() {
        BigDecimal valorBruto = new BigDecimal("10000.00");
        BigDecimal taxaBase = new BigDecimal("0.10");
        BigDecimal spreadRisco = BigDecimal.ZERO;
        BigDecimal custoOperacional = BigDecimal.ZERO;

        ResultadoDesagio prazoCurto = calculadora.calcular(valorBruto, 1, taxaBase, spreadRisco, custoOperacional);
        ResultadoDesagio prazoLongo = calculadora.calcular(valorBruto, 6, taxaBase, spreadRisco, custoOperacional);

        assertThat(prazoLongo.valorDesagio()).isGreaterThan(prazoCurto.valorDesagio());
        assertThat(prazoLongo.valorPresente()).isLessThan(prazoCurto.valorPresente());
    }

    @Test
    void resultadosSaoArredondadosComEscalaMonetariaDeDuasCasas() {
        ResultadoDesagio resultado = calculadora.calcular(
                new BigDecimal("999.99"), 1, new BigDecimal("0.008469"),
                new BigDecimal("0.004472"), new BigDecimal("0.000416"));

        assertThat(resultado.valorPresente().scale()).isEqualTo(2);
        assertThat(resultado.valorDesagio().scale()).isEqualTo(2);
    }
}
