package com.srmasset.creditengine.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Converte o resultado de um deságio para a moeda de pagamento quando ela
 * difere da moeda do título (cross-currency - ver SPEC.md, "Premissas
 * adotadas" item 3). A conversão acontece "ao final": o deságio já foi
 * calculado inteiramente na moeda do título (taxaBase/spread daquela moeda),
 * e só então valorPresente/valorDesagio sao convertidos.
 *
 * <p>Convenção: {@code cotacaoCambio} é sempre "quantidade de BRL por 1 USD"
 * (padrão de mercado, ex. PTAX), independente de qual das duas moedas e' o
 * título - simplificação válida enquanto só existem duas moedas.
 */
public class ConversorCambial {

    private static final MathContext CONTEXTO_CALCULO = MathContext.DECIMAL128;
    private static final int ESCALA_MONETARIA = 2;

    public ResultadoDesagio converter(ResultadoDesagio resultado, BigDecimal valorBruto,
                                       Moeda moedaTitulo, Moeda moedaPagamento, BigDecimal cotacaoCambio) {
        if (moedaTitulo == moedaPagamento) {
            return resultado;
        }

        BigDecimal valorBrutoConvertido = converterValor(valorBruto, moedaTitulo, cotacaoCambio)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_EVEN);
        BigDecimal valorPresenteConvertido = converterValor(resultado.valorPresente(), moedaTitulo, cotacaoCambio)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_EVEN);

        // deságio derivado por subtração na moeda de pagamento, preservando o invariante
        // valorPresente + deságio == valorBruto (agora convertido) - mesma tecnica usada
        // em CalculadoraDesagio para a moeda original.
        BigDecimal valorDesagioConvertido = valorBrutoConvertido.subtract(valorPresenteConvertido)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_EVEN);

        return new ResultadoDesagio(valorPresenteConvertido, valorDesagioConvertido, resultado.taxaDescontoAplicada());
    }

    private BigDecimal converterValor(BigDecimal valor, Moeda moedaTitulo, BigDecimal cotacaoCambio) {
        return moedaTitulo == Moeda.BRL
                ? valor.divide(cotacaoCambio, CONTEXTO_CALCULO)
                : valor.multiply(cotacaoCambio, CONTEXTO_CALCULO);
    }
}
