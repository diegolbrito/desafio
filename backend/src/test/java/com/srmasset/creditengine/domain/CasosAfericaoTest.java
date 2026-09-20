package com.srmasset.creditengine.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Casos de aferição fornecidos pelo negócio para validar a fórmula de deságio
 * e a conversão cambial (ver SPEC.md, "Premissas adotadas" itens 1 e 3).
 * Cobre juros compostos mensais (C1/C2) e cross-currency com deságio mantido
 * na moeda do título, só o valorPresente convertido (C3).
 */
class CasosAfericaoTest {

    private final CalculadoraDesagio calculadora = new CalculadoraDesagio();
    private final ConversorCambial conversor = new ConversorCambial();

    @Test
    void c1DuplicataMercantilBrlSemCambio() {
        ResultadoDesagio resultado = calculadora.calcular(
                new BigDecimal("100000.00"), 3, new BigDecimal("0.025"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(resultado.valorPresente()).isEqualByComparingTo("92859.94");
        assertThat(resultado.valorDesagio()).isEqualByComparingTo("7140.06");
    }

    @Test
    void c2ChequePreDatadoBrlSemCambio() {
        ResultadoDesagio resultado = calculadora.calcular(
                new BigDecimal("25000.00"), 2, new BigDecimal("0.035"), BigDecimal.ZERO, BigDecimal.ZERO);

        assertThat(resultado.valorPresente()).isEqualByComparingTo("23337.77");
        assertThat(resultado.valorDesagio()).isEqualByComparingTo("1662.23");
    }

    @Test
    void c3DuplicataMercantilComPagamentoEmUsd() {
        // Mesmo titulo do C1 (BRL, mesma taxa, mesmo prazo), mas pago em USD.
        ResultadoDesagio resultado = calculadora.calcular(
                new BigDecimal("100000.00"), 3, new BigDecimal("0.025"), BigDecimal.ZERO, BigDecimal.ZERO);
        ResultadoDesagio convertido = conversor.converter(resultado, Moeda.USD, new BigDecimal("5.4321"));

        assertThat(convertido.valorPresente()).isEqualByComparingTo("17094.67");
        // deságio permanece na moeda do titulo (BRL), identico ao C1 - nao e' convertido.
        assertThat(convertido.valorDesagio()).isEqualByComparingTo("7140.06");
    }
}
