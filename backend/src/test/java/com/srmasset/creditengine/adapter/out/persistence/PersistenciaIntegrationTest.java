package com.srmasset.creditengine.adapter.out.persistence;

import com.srmasset.creditengine.adapter.out.persistence.entity.TransacaoEventoEntity;
import com.srmasset.creditengine.adapter.out.persistence.repository.LoteRecebivelJpaRepository;
import com.srmasset.creditengine.adapter.out.persistence.repository.TransacaoEventoJpaRepository;
import com.srmasset.creditengine.application.metrics.CreditEngineMetrics;
import com.srmasset.creditengine.application.port.in.ComandoPrecificarLote;
import com.srmasset.creditengine.application.port.out.CotacaoCambioPort;
import com.srmasset.creditengine.application.service.PrecificarLoteService;
import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.EventoTransacao;
import com.srmasset.creditengine.domain.LoteRecebiveis;
import com.srmasset.creditengine.domain.Moeda;
import com.srmasset.creditengine.domain.Recebivel;
import com.srmasset.creditengine.domain.ResultadoDesagio;
import com.srmasset.creditengine.domain.StatusLote;
import com.srmasset.creditengine.domain.StatusRecebivel;
import com.srmasset.creditengine.domain.TipoEventoTransacao;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.byLessThan;

/**
 * Sobe um Postgres real via Testcontainers e roda as migrations Flyway de
 * verdade, validando que o mapeamento JPA bate com o schema (ddl-auto=validate)
 * e que os adapters de persistencia (ports de saida da Etapa 2) funcionam
 * ponta a ponta, inclusive orquestrados pelo PrecificarLoteService.
 */
@SpringBootTest
@Testcontainers
@Transactional
class PersistenciaIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void configurarDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private TaxaBasePersistenceAdapter taxaBaseAdapter;
    @Autowired
    private CategoriaRiscoPersistenceAdapter categoriaRiscoAdapter;
    @Autowired
    private LoteRecebiveisPersistenceAdapter loteAdapter;
    @Autowired
    private TransacaoEventoPersistenceAdapter eventoAdapter;
    @Autowired
    private LoteRecebivelJpaRepository loteRepository;
    @Autowired
    private TransacaoEventoJpaRepository eventoRepository;
    @Autowired
    private RecebivelLiquidacaoPersistenceAdapter liquidacaoAdapter;

    @Test
    void seedsDeTaxaBaseEstaoDisponiveis() {
        assertThat(taxaBaseAdapter.buscarTaxaVigente(Moeda.BRL)).isEqualByComparingTo("0.010000");
        assertThat(taxaBaseAdapter.buscarTaxaVigente(Moeda.USD)).isEqualByComparingTo("0.010000");
    }

    @Test
    void seedsDeCategoriaRiscoEstaoDisponiveis() {
        assertThat(categoriaRiscoAdapter.buscarSpread(CategoriaRisco.AA)).isEqualByComparingTo("0.015000");
        assertThat(categoriaRiscoAdapter.buscarSpread(CategoriaRisco.E)).isEqualByComparingTo("0.009489");
    }

    @Test
    void salvaLoteComRecebiveisEAtribuiIds() {
        Recebivel recebivel = Recebivel.criar("Ativo Teste", new BigDecimal("1000.00"),
                LocalDate.now().plusDays(30), CategoriaRisco.B);
        recebivel.aplicarPrecificacao(new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000")));
        LoteRecebiveis lote = LoteRecebiveis.criar(LocalDate.now(), List.of(recebivel));
        lote.marcarPrecificado();

        LoteRecebiveis salvo = loteAdapter.salvar(lote);

        assertThat(salvo.getId()).isNotNull();
        assertThat(salvo.getRecebiveis().get(0).getId()).isNotNull();
        assertThat(loteRepository.findById(salvo.getId())).isPresent();
        assertThat(loteRepository.findById(salvo.getId()).orElseThrow().getRecebiveis()).hasSize(1);
    }

    @Test
    void salvaRecebivelCrossCurrencyComMoedaPagamentoECotacao() {
        Recebivel recebivel = Recebivel.criar("Ativo Teste", new BigDecimal("1000.00"),
                LocalDate.now().plusDays(30), CategoriaRisco.B, Moeda.USD);
        recebivel.aplicarPrecificacao(new ResultadoDesagio(
                new BigDecimal("182.69"), new BigDecimal("9.62"), new BigDecimal("0.105000")), new BigDecimal("5.20"));
        LoteRecebiveis lote = LoteRecebiveis.criar(LocalDate.now(), List.of(recebivel));
        lote.marcarPrecificado();

        loteAdapter.salvar(lote);

        var recebivelPersistido = loteRepository.buscarComRecebiveisPorId(lote.getId())
                .orElseThrow().getRecebiveis().get(0);
        assertThat(recebivelPersistido.getMoeda()).isEqualTo(Moeda.BRL);
        assertThat(recebivelPersistido.getMoedaPagamento()).isEqualTo(Moeda.USD);
        assertThat(recebivelPersistido.getCotacaoCambio()).isEqualByComparingTo("5.20");
    }

    @Test
    void registraEventoDeTransacaoReferenciandoLotePersistido() {
        Recebivel recebivel = Recebivel.criar("Ativo Teste", new BigDecimal("1000.00"),
                LocalDate.now().plusDays(30), CategoriaRisco.B);
        LoteRecebiveis lote = LoteRecebiveis.criar(LocalDate.now(), List.of(recebivel));
        LoteRecebiveis salvo = loteAdapter.salvar(lote);

        EventoTransacao evento = EventoTransacao.loteRecebido(salvo.getId(), OffsetDateTime.now());
        eventoAdapter.registrar(evento);

        List<TransacaoEventoEntity> eventos = eventoRepository.findAll();
        assertThat(eventos).anySatisfy(e -> {
            assertThat(e.getLoteRecebivelId()).isEqualTo(salvo.getId());
            assertThat(e.getTipo()).isEqualTo(TipoEventoTransacao.LOTE_RECEBIDO);
        });
    }

    @Test
    void precificaLoteCompletoUsandoAdaptersReais() {
        // lote 100% BRL (moedaPagamento null) - nunca deveria chamar o servico de cambio
        CotacaoCambioPort cotacaoCambioPort = () -> {
            throw new UnsupportedOperationException("lote sem cross-currency nao deveria buscar cotacao");
        };
        PrecificarLoteService service = new PrecificarLoteService(
                taxaBaseAdapter, categoriaRiscoAdapter, loteAdapter, eventoAdapter, cotacaoCambioPort,
                new BigDecimal("0.005"), Clock.systemUTC(),
                new CreditEngineMetrics(new SimpleMeterRegistry()));

        ComandoPrecificarLote comando = new ComandoPrecificarLote(List.of(
                new ComandoPrecificarLote.ComandoRecebivel("Ativo Integracao", new BigDecimal("5000.00"),
                        LocalDate.now().plusDays(60), CategoriaRisco.C, null)
        ));

        LoteRecebiveis lote = service.precificar(comando);

        assertThat(lote.getStatus()).isEqualTo(StatusLote.PRECIFICADO);
        assertThat(lote.getRecebiveis().get(0).getValorPresente()).isNotNull();
        assertThat(eventoRepository.findAll()).hasSize(3); // lote_recebido + recebivel_precificado + lote_precificado
    }

    @Test
    void buscarParaLiquidarERecuperarEstadoPersistidoDeUmRecebivelPrecificado() {
        Recebivel recebivel = Recebivel.criar("Ativo Teste", new BigDecimal("1000.00"),
                LocalDate.now().plusDays(30), CategoriaRisco.B);
        recebivel.aplicarPrecificacao(new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000")));
        LoteRecebiveis lote = LoteRecebiveis.criar(LocalDate.now(), List.of(recebivel));
        lote.marcarPrecificado();
        LoteRecebiveis salvo = loteAdapter.salvar(lote);
        UUID recebivelId = salvo.getRecebiveis().get(0).getId();

        Recebivel encontrado = liquidacaoAdapter.buscarParaLiquidar(salvo.getId(), recebivelId).orElseThrow();

        assertThat(encontrado.getStatus()).isEqualTo(StatusRecebivel.PRECIFICADO);
        assertThat(encontrado.getValorPresente()).isEqualByComparingTo("950.00");
    }

    @Test
    void salvarLiquidacaoPersisteStatusLiquidadoEInstante() {
        Recebivel recebivel = Recebivel.criar("Ativo Teste", new BigDecimal("1000.00"),
                LocalDate.now().plusDays(30), CategoriaRisco.B);
        recebivel.aplicarPrecificacao(new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000")));
        LoteRecebiveis lote = LoteRecebiveis.criar(LocalDate.now(), List.of(recebivel));
        lote.marcarPrecificado();
        LoteRecebiveis salvo = loteAdapter.salvar(lote);
        UUID recebivelId = salvo.getRecebiveis().get(0).getId();

        Recebivel paraLiquidar = liquidacaoAdapter.buscarParaLiquidar(salvo.getId(), recebivelId).orElseThrow();
        OffsetDateTime agora = OffsetDateTime.now();
        paraLiquidar.liquidar(agora);
        liquidacaoAdapter.salvar(paraLiquidar);

        var persistido = loteRepository.buscarComRecebiveisPorId(salvo.getId()).orElseThrow().getRecebiveis().get(0);
        assertThat(persistido.getStatus()).isEqualTo(StatusRecebivel.LIQUIDADO);
        assertThat(persistido.getLiquidadoEm()).isCloseTo(agora, byLessThan(1, ChronoUnit.SECONDS));
    }
}
