package com.srmasset.creditengine.config;

import com.srmasset.creditengine.application.port.in.BuscarLoteRecebiveisUseCase;
import com.srmasset.creditengine.application.port.in.LiquidarRecebivelUseCase;
import com.srmasset.creditengine.application.port.in.ListarLotesRecebiveisUseCase;
import com.srmasset.creditengine.application.port.in.PrecificarLoteUseCase;
import com.srmasset.creditengine.application.port.out.BuscarLoteRecebiveisPort;
import com.srmasset.creditengine.application.port.out.CategoriaRiscoRepositoryPort;
import com.srmasset.creditengine.application.port.out.LiquidarRecebivelPort;
import com.srmasset.creditengine.application.port.out.ListarLotesRecebiveisPort;
import com.srmasset.creditengine.application.port.out.RegistrarEventoTransacaoPort;
import com.srmasset.creditengine.application.port.out.SalvarLoteRecebiveisPort;
import com.srmasset.creditengine.application.port.out.TaxaBaseRepositoryPort;
import com.srmasset.creditengine.application.service.BuscarLoteRecebiveisService;
import com.srmasset.creditengine.application.service.LiquidarRecebivelService;
import com.srmasset.creditengine.application.service.ListarLotesRecebiveisService;
import com.srmasset.creditengine.application.service.PrecificarLoteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.Clock;

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
    public PrecificarLoteUseCase precificarLoteUseCase(
            TaxaBaseRepositoryPort taxaBaseRepository,
            CategoriaRiscoRepositoryPort categoriaRiscoRepository,
            SalvarLoteRecebiveisPort salvarLotePort,
            RegistrarEventoTransacaoPort registrarEventoPort,
            @Value("${credit-engine.custo-operacional}") BigDecimal custoOperacionalPadrao,
            @Value("${credit-engine.cotacao-cambio}") BigDecimal cotacaoCambioPadrao,
            Clock clock) {
        return new PrecificarLoteService(taxaBaseRepository, categoriaRiscoRepository,
                salvarLotePort, registrarEventoPort, custoOperacionalPadrao, cotacaoCambioPadrao, clock);
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
                                                               Clock clock) {
        return new LiquidarRecebivelService(liquidarRecebivelPort, registrarEventoPort, clock);
    }
}
