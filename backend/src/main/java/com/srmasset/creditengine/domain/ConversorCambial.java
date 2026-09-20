package com.srmasset.creditengine.domain;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Converte apenas o valorPresente para a moeda de pagamento quando ela difere
 * de BRL (cross-currency - ver SPEC.md, "Premissas adotadas" item 3). O ativo
 * e' sempre denominado em BRL, entao a unica conversao possivel e' BRL para a
 * moeda de pagamento (nunca o contrario). O deságio já foi calculado
 * inteiramente em BRL (taxaBase/spread) e permanece sempre em BRL, mesmo com
 * conversão - representa o custo do desconto no referencial do próprio
 * ativo, não um valor a pagar. Por isso o invariante "valorPresente + deságio
 * == valorBruto" só vale quando não há cross-currency; com conversão,
 * valorPresente fica na moeda de pagamento e valorBruto/deságio ficam em BRL.
 *
 * <p>Convenção: {@code cotacaoCambio} é sempre "quantidade de BRL por 1 USD"
 * (padrão de mercado, ex. PTAX).
 */
public class ConversorCambial {

    private static final MathContext CONTEXTO_CALCULO = MathContext.DECIMAL128;
    private static final int ESCALA_MONETARIA = 2;

    public ResultadoDesagio converter(ResultadoDesagio resultado, Moeda moedaPagamento, BigDecimal cotacaoCambio) {
        if (moedaPagamento == Moeda.BRL) {
            return resultado;
        }

        BigDecimal valorPresenteConvertido = resultado.valorPresente()
                .divide(cotacaoCambio, CONTEXTO_CALCULO)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_EVEN);

        return new ResultadoDesagio(valorPresenteConvertido, resultado.valorDesagio(), resultado.taxaDescontoAplicada());
    }
}
