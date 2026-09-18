package com.srmasset.creditengine.adapter.out.persistence;

import com.srmasset.creditengine.adapter.out.persistence.entity.TransacaoEventoEntity;
import com.srmasset.creditengine.adapter.out.persistence.repository.LoteRecebivelJpaRepository;
import com.srmasset.creditengine.adapter.out.persistence.repository.TransacaoEventoJpaRepository;
import com.srmasset.creditengine.application.port.in.ComandoPrecificarLote;
import com.srmasset.creditengine.application.service.PrecificarLoteService;
import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.EventoTransacao;
import com.srmasset.creditengine.domain.LoteRecebiveis;
import com.srmasset.creditengine.domain.Moeda;
import com.srmasset.creditengine.domain.Recebivel;
import com.srmasset.creditengine.domain.ResultadoDesagio;
import com.srmasset.creditengine.domain.StatusLote;
import com.srmasset.creditengine.domain.TipoEventoTransacao;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void seedsDeTaxaBaseEstaoDisponiveis() {
        assertThat(taxaBaseAdapter.buscarTaxaVigente(Moeda.BRL)).isEqualByComparingTo("0.106500");
        assertThat(taxaBaseAdapter.buscarTaxaVigente(Moeda.USD)).isEqualByComparingTo("0.048000");
    }

    @Test
    void seedsDeCategoriaRiscoEstaoDisponiveis() {
        assertThat(categoriaRiscoAdapter.buscarSpread(CategoriaRisco.AA)).isEqualByComparingTo("0.010000");
        assertThat(categoriaRiscoAdapter.buscarSpread(CategoriaRisco.E)).isEqualByComparingTo("0.120000");
    }

    @Test
    void salvaLoteComRecebiveisEAtribuiIds() {
        Recebivel recebivel = Recebivel.criar("Cedente Teste", new BigDecimal("1000.00"), Moeda.BRL,
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
    void registraEventoDeTransacaoReferenciandoLotePersistido() {
        Recebivel recebivel = Recebivel.criar("Cedente Teste", new BigDecimal("1000.00"), Moeda.BRL,
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
        PrecificarLoteService service = new PrecificarLoteService(
                taxaBaseAdapter, categoriaRiscoAdapter, loteAdapter, eventoAdapter,
                new BigDecimal("0.005"), Clock.systemUTC());

        ComandoPrecificarLote comando = new ComandoPrecificarLote(List.of(
                new ComandoPrecificarLote.ComandoRecebivel("Cedente Integracao", new BigDecimal("5000.00"),
                        Moeda.BRL, LocalDate.now().plusDays(60), CategoriaRisco.C)
        ));

        LoteRecebiveis lote = service.precificar(comando);

        assertThat(lote.getStatus()).isEqualTo(StatusLote.PRECIFICADO);
        assertThat(lote.getRecebiveis().get(0).getValorPresente()).isNotNull();
        assertThat(eventoRepository.findAll()).hasSize(3); // lote_recebido + recebivel_precificado + lote_precificado
    }
}
