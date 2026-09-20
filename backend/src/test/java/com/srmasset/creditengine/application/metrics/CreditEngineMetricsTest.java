package com.srmasset.creditengine.application.metrics;

import com.srmasset.creditengine.domain.Moeda;
import com.srmasset.creditengine.domain.StatusLote;
import com.srmasset.creditengine.domain.StatusRecebivel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CreditEngineMetricsTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final CreditEngineMetrics metrics = new CreditEngineMetrics(registry);

    @Test
    void registraContadorDeLotesPrecificadosPorStatus() {
        metrics.registrarLotePrecificado(StatusLote.PRECIFICADO);
        metrics.registrarLotePrecificado(StatusLote.PRECIFICADO);
        metrics.registrarLotePrecificado(StatusLote.ERRO);

        assertThat(registry.counter("creditengine.lotes.precificados", "status", "PRECIFICADO").count())
                .isEqualTo(2.0);
        assertThat(registry.counter("creditengine.lotes.precificados", "status", "ERRO").count())
                .isEqualTo(1.0);
    }

    @Test
    void registraContadorDeRecebiveisProcessadosPorStatus() {
        metrics.registrarRecebivelProcessado(StatusRecebivel.PRECIFICADO);
        metrics.registrarRecebivelProcessado(StatusRecebivel.REJEITADO);

        assertThat(registry.counter("creditengine.recebiveis.processados", "status", "PRECIFICADO").count())
                .isEqualTo(1.0);
        assertThat(registry.counter("creditengine.recebiveis.processados", "status", "REJEITADO").count())
                .isEqualTo(1.0);
    }

    @Test
    void registraDistribuicaoDeValorPrecificadoSeparadaPorMoeda() {
        metrics.registrarValorPrecificado(new BigDecimal("950.00"), Moeda.BRL);
        metrics.registrarValorPrecificado(new BigDecimal("190.00"), Moeda.USD);

        assertThat(registry.summary("creditengine.valor.precificado", "moeda", "BRL").totalAmount())
                .isEqualTo(950.00);
        assertThat(registry.summary("creditengine.valor.precificado", "moeda", "USD").totalAmount())
                .isEqualTo(190.00);
    }

    @Test
    void registraContadorEDistribuicaoDeValorLiquidado() {
        metrics.registrarRecebivelLiquidado(new BigDecimal("974.75"), Moeda.BRL);

        assertThat(registry.counter("creditengine.recebiveis.liquidados", "moeda", "BRL").count())
                .isEqualTo(1.0);
        assertThat(registry.summary("creditengine.valor.liquidado", "moeda", "BRL").totalAmount())
                .isEqualTo(974.75);
    }
}
