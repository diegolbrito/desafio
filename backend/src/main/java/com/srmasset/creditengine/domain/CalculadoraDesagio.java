package com.srmasset.creditengine.domain;

import ch.obermuhlner.math.big.BigDecimalMath;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;

/**
 * Implementa a formula de desagio definida em SPEC.md ("Premissas adotadas" - item 1):
 * desconto composto por valor presente, com taxa de desconto anual composta por
 * taxaBase(moeda) + spreadRisco(categoria) + custoOperacional, e base de dias por moeda.
 *
 * <p>Calculos intermediarios usam MathContext de alta precisao para nao perder informacao
 * antes do arredondamento final; o resultado (valorPresente/valorDesagio) e' arredondado
 * HALF_EVEN em 2 casas decimais somente ao final, conforme decisao de precisao numerica.
 */
public class CalculadoraDesagio {

    private static final MathContext CONTEXTO_CALCULO = MathContext.DECIMAL128;
    private static final int ESCALA_MONETARIA = 2;
    private static final int ESCALA_TAXA = 6;

    /**
     * @param valorBruto        valor de face do recebivel
     * @param moeda             moeda do recebivel (define a base de dias)
     * @param prazoDias         dias corridos ate o vencimento (ver {@link Recebivel#calcularPrazoDias})
     * @param taxaBase          taxa base de mercado vigente para a moeda (fracao decimal, a.a.)
     * @param spreadRisco       spread da categoria de risco do recebivel (fracao decimal, a.a.)
     * @param custoOperacional  spread fixo de custo operacional (fracao decimal, a.a.)
     */
    public ResultadoDesagio calcular(BigDecimal valorBruto, Moeda moeda, long prazoDias,
                                      BigDecimal taxaBase, BigDecimal spreadRisco, BigDecimal custoOperacional) {
        BigDecimal taxaDesconto = taxaBase
                .add(spreadRisco, CONTEXTO_CALCULO)
                .add(custoOperacional, CONTEXTO_CALCULO);

        BigDecimal expoente = BigDecimal.valueOf(prazoDias)
                .divide(BigDecimal.valueOf(moeda.baseDias()), CONTEXTO_CALCULO);

        BigDecimal baseComposta = BigDecimal.ONE.add(taxaDesconto, CONTEXTO_CALCULO);
        BigDecimal fatorDesconto = BigDecimalMath.pow(baseComposta, expoente, CONTEXTO_CALCULO);

        BigDecimal valorPresente = valorBruto
                .divide(fatorDesconto, CONTEXTO_CALCULO)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_EVEN);

        // deságio derivado por subtração de valores já na escala final, garantindo
        // que valorPresente + deságio == valorBruto exatamente (invariante de auditoria).
        BigDecimal valorDesagio = valorBruto.subtract(valorPresente)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_EVEN);

        BigDecimal taxaDescontoAplicada = taxaDesconto.setScale(ESCALA_TAXA, RoundingMode.HALF_EVEN);

        return new ResultadoDesagio(valorPresente, valorDesagio, taxaDescontoAplicada);
    }
}
