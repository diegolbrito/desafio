package com.srmasset.creditengine.config;

import com.srmasset.creditengine.application.metrics.CreditEngineMetrics;
import com.srmasset.creditengine.application.port.in.BuscarLoteRecebiveisUseCase;
import com.srmasset.creditengine.application.port.in.LiquidarRecebivelUseCase;
import com.srmasset.creditengine.application.port.in.ListarLotesRecebiveisUseCase;
import com.srmasset.creditengine.application.port.in.PrecificarLoteUseCase;
import com.srmasset.creditengine.application.port.out.BuscarLoteRecebiveisPort;
import com.srmasset.creditengine.application.port.out.CategoriaRiscoRepositoryPort;
import com.srmasset.creditengine.application.port.out.CotacaoCambioPort;
import com.srmasset.creditengine.application.port.out.LiquidarRecebivelPort;
import com.srmasset.creditengine.application.port.out.ListarLotesRecebiveisPort;
import com.srmasset.creditengine.application.port.out.RegistrarEventoTransacaoPort;
import com.srmasset.creditengine.application.port.out.SalvarLoteRecebiveisPort;
import com.srmasset.creditengine.application.port.out.TaxaBaseRepositoryPort;
import com.srmasset.creditengine.application.service.BuscarLoteRecebiveisService;
import com.srmasset.creditengine.application.service.LiquidarRecebivelService;
import com.srmasset.creditengine.application.service.ListarLotesRecebiveisService;
import com.srmasset.creditengine.application.service.PrecificarLoteService;
import com.srmasset.creditengine.adapter.out.http.CotacaoCambioHttpAdapter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;

/**
 * Camada de composicao (entrypoint): instancia os casos de uso (classes de
 * aplicacao puras, sem anotacoes do Spring) e injeta os adapters de saida
 * concretos. Ver SPEC.md: "A injecao de dependencia deve ser feita na camada
 * de composicao, fora do dominio."
 */
@Configuration
public class UseCaseConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    public CreditEngineMetrics creditEngineMetrics(MeterRegistry registry) {
        return new CreditEngineMetrics(registry);
    }

    @Bean
    public CotacaoCambioPort cotacaoCambioPort(
            @Value("${credit-engine.cotacao-cambio.service-url}") String serviceUrl,
            @Value("${credit-engine.cotacao-cambio.timeout-conexao-ms}") long timeoutConexaoMs,
            @Value("${credit-engine.cotacao-cambio.timeout-leitura-ms}") long timeoutLeituraMs,
            @Value("${credit-engine.cotacao-cambio.fallback}") BigDecimal cotacaoFallback,
            @Value("${credit-engine.cotacao-cambio.circuit-breaker.limite-falhas}") int limiteFalhasConsecutivas,
            @Value("${credit-engine.cotacao-cambio.circuit-breaker.janela-aberto-segundos}") long janelaAbertoSegundos,
            Clock clock,
            CreditEngineMetrics metrics) {
        return new CotacaoCambioHttpAdapter(serviceUrl, Duration.ofMillis(timeoutConexaoMs),
                Duration.ofMillis(timeoutLeituraMs), cotacaoFallback, limiteFalhasConsecutivas,
                Duration.ofSeconds(janelaAbertoSegundos), clock, metrics);
    }

    @Bean
    public PrecificarLoteUseCase precificarLoteUseCase(
            TaxaBaseRepositoryPort taxaBaseRepository,
            CategoriaRiscoRepositoryPort categoriaRiscoRepository,
            SalvarLoteRecebiveisPort salvarLotePort,
            RegistrarEventoTransacaoPort registrarEventoPort,
            CotacaoCambioPort cotacaoCambioPort,
            @Value("${credit-engine.custo-operacional}") BigDecimal custoOperacionalPadrao,
            Clock clock,
            CreditEngineMetrics metrics) {
        return new PrecificarLoteService(taxaBaseRepository, categoriaRiscoRepository,
                salvarLotePort, registrarEventoPort, cotacaoCambioPort, custoOperacionalPadrao, clock, metrics);
    }

    @Bean
    public ListarLotesRecebiveisUseCase listarLotesRecebiveisUseCase(ListarLotesRecebiveisPort listarLotesPort) {
        return new ListarLotesRecebiveisService(listarLotesPort);
    }

    @Bean
    public BuscarLoteRecebiveisUseCase buscarLoteRecebiveisUseCase(BuscarLoteRecebiveisPort buscarLotePort) {
        return new BuscarLoteRecebiveisService(buscarLotePort);
    }

    @Bean
    public LiquidarRecebivelUseCase liquidarRecebivelUseCase(LiquidarRecebivelPort liquidarRecebivelPort,
                                                               RegistrarEventoTransacaoPort registrarEventoPort,
                                                               Clock clock,
                                                               CreditEngineMetrics metrics) {
        return new LiquidarRecebivelService(liquidarRecebivelPort, registrarEventoPort, clock, metrics);
    }
}
