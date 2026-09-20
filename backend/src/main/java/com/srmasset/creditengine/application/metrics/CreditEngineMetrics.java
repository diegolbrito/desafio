package com.srmasset.creditengine.application.metrics;

import com.srmasset.creditengine.domain.Moeda;
import com.srmasset.creditengine.domain.StatusLote;
import com.srmasset.creditengine.domain.StatusRecebivel;
import io.micrometer.core.instrument.MeterRegistry;

import java.math.BigDecimal;

/**
 * Metricas de negocio (Micrometer/Prometheus), distintas dos eventos de
 * auditoria (EventoTransacao): metricas sao agregadas/aproximadas para
 * dashboards (por isso convertem BigDecimal para double - nunca fazer isso
 * para valores usados em calculo/persistencia, so' para observabilidade),
 * enquanto o evento de auditoria e' o registro fiel e individual de cada fato
 * (ver SPEC.md, "Premissas adotadas" - observabilidade).
 *
 * <p>{@link MeterRegistry} e' a API vendor-neutral do Micrometer (nao uma
 * anotacao do Spring), por isso pode ser usada aqui sem violar a regra da
 * camada de aplicacao ser livre de framework - mesmo racional ja aplicado ao
 * uso direto de SLF4J nos services.
 */
public class CreditEngineMetrics {

    private final MeterRegistry registry;

    public CreditEngineMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void registrarLotePrecificado(StatusLote status) {
        registry.counter("creditengine.lotes.precificados", "status", status.name()).increment();
    }

    public void registrarRecebivelProcessado(StatusRecebivel status) {
        registry.counter("creditengine.recebiveis.processados", "status", status.name()).increment();
    }

    public void registrarValorPrecificado(BigDecimal valorPresente, Moeda moedaPagamento) {
        registry.summary("creditengine.valor.precificado", "moeda", moedaPagamento.name())
                .record(valorPresente.doubleValue());
    }

    /** So' deve ser chamado na liquidacao efetiva - nunca numa chamada idempotente repetida. */
    public void registrarRecebivelLiquidado(BigDecimal valorPresente, Moeda moedaPagamento) {
        registry.counter("creditengine.recebiveis.liquidados", "moeda", moedaPagamento.name()).increment();
        registry.summary("creditengine.valor.liquidado", "moeda", moedaPagamento.name())
                .record(valorPresente.doubleValue());
    }

    /**
     * @param origem "externa" (servico de cambio respondeu), "cache" (ultimo valor bom
     *               conhecido, servico fora) ou "fallback_estatico" (nunca teve valor bom,
     *               cold start com servico fora) - ver CotacaoCambioHttpAdapter.
     */
    public void registrarCotacaoOrigem(String origem) {
        registry.counter("creditengine.cotacao.consultas", "origem", origem).increment();
    }
}
