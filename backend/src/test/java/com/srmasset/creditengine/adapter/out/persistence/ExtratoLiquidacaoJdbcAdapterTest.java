package com.srmasset.creditengine.adapter.out.persistence;

import com.srmasset.creditengine.application.port.out.ExtratoLiquidacaoItem;
import com.srmasset.creditengine.application.port.out.FiltroExtratoLiquidacao;
import com.srmasset.creditengine.application.port.out.PaginaResultado;
import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.LoteRecebiveis;
import com.srmasset.creditengine.domain.Moeda;
import com.srmasset.creditengine.domain.Recebivel;
import com.srmasset.creditengine.domain.ResultadoDesagio;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa o adapter de SQL nativo (ver SPEC.md item 12) contra um Postgres real
 * via Testcontainers - inclusive os indices/extensao pg_trgm da migration
 * V202609181011, ja que rodam as migrations Flyway de verdade.
 */
@SpringBootTest
@Testcontainers
@Transactional
class ExtratoLiquidacaoJdbcAdapterTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void configurarDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ExtratoLiquidacaoJdbcAdapter adapter;
    @Autowired
    private LoteRecebiveisPersistenceAdapter loteAdapter;
    @Autowired
    private RecebivelLiquidacaoPersistenceAdapter liquidacaoAdapter;
    @PersistenceContext
    private EntityManager entityManager;

    private static final OffsetDateTime T1 = OffsetDateTime.of(2026, 1, 10, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T2 = OffsetDateTime.of(2026, 2, 15, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final OffsetDateTime T3 = OffsetDateTime.of(2026, 3, 20, 12, 0, 0, 0, ZoneOffset.UTC);

    @BeforeEach
    void seedRecebiveisLiquidados() {
        criarELiquidar("Duplicata Mercantil A", Moeda.BRL, T1);
        criarELiquidar("Duplicata Mercantil B", Moeda.USD, T2);
        criarELiquidar("Cheque Pre-datado", Moeda.BRL, T3);
        // nao liquidado - nunca deve aparecer no extrato
        Recebivel pendente = Recebivel.criar("Nao liquidado", new BigDecimal("100.00"),
                LocalDate.now().plusDays(30), CategoriaRisco.B);
        loteAdapter.salvar(LoteRecebiveis.criar(LocalDate.now(), List.of(pendente)));

        // o adapter sob teste le via JdbcTemplate puro, fora da sessao do Hibernate - por isso
        // precisa de um flush explicito: sem ele, a ultima atualizacao (liquidacao) desta sequencia
        // fica pendente no persistence context e nao seria visivel para a query SQL nativa.
        entityManager.flush();
    }

    private void criarELiquidar(String ativo, Moeda moedaPagamento, OffsetDateTime quando) {
        Recebivel recebivel = Recebivel.criar(ativo, new BigDecimal("1000.00"),
                LocalDate.now().plusDays(60), CategoriaRisco.B, moedaPagamento);
        recebivel.aplicarPrecificacao(new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000")),
                moedaPagamento == Moeda.BRL ? null : new BigDecimal("5.00"));
        LoteRecebiveis lote = LoteRecebiveis.criar(LocalDate.now(), List.of(recebivel));
        lote.marcarPrecificado();
        LoteRecebiveis salvo = loteAdapter.salvar(lote);
        java.util.UUID recebivelId = salvo.getRecebiveis().get(0).getId();

        Recebivel paraLiquidar = liquidacaoAdapter.buscarParaLiquidar(salvo.getId(), recebivelId).orElseThrow();
        paraLiquidar.liquidar(quando);
        liquidacaoAdapter.salvar(paraLiquidar);
    }

    @Test
    void listaTodosOsLiquidadosSemFiltro() {
        PaginaResultado<ExtratoLiquidacaoItem> pagina = adapter.buscar(
                new FiltroExtratoLiquidacao(null, null, null, null), 0, 20);

        assertThat(pagina.totalElements()).isEqualTo(3);
        assertThat(pagina.content()).hasSize(3);
        // ordenado por liquidadoEm desc
        assertThat(pagina.content().get(0).ativo()).isEqualTo("Cheque Pre-datado");
        assertThat(pagina.content().get(2).ativo()).isEqualTo("Duplicata Mercantil A");
    }

    @Test
    void filtraPorPeriodo() {
        PaginaResultado<ExtratoLiquidacaoItem> pagina = adapter.buscar(
                new FiltroExtratoLiquidacao(T2.minusDays(1), T3.plusDays(1), null, null), 0, 20);

        assertThat(pagina.totalElements()).isEqualTo(2);
        assertThat(pagina.content()).extracting(ExtratoLiquidacaoItem::ativo)
                .containsExactlyInAnyOrder("Duplicata Mercantil B", "Cheque Pre-datado");
    }

    @Test
    void filtraPorAtivoBuscaParcialCaseInsensitive() {
        PaginaResultado<ExtratoLiquidacaoItem> pagina = adapter.buscar(
                new FiltroExtratoLiquidacao(null, null, "duplicata", null), 0, 20);

        assertThat(pagina.totalElements()).isEqualTo(2);
        assertThat(pagina.content()).extracting(ExtratoLiquidacaoItem::ativo)
                .containsExactlyInAnyOrder("Duplicata Mercantil A", "Duplicata Mercantil B");
    }

    @Test
    void filtraPorMoeda() {
        PaginaResultado<ExtratoLiquidacaoItem> pagina = adapter.buscar(
                new FiltroExtratoLiquidacao(null, null, null, Moeda.USD), 0, 20);

        assertThat(pagina.totalElements()).isEqualTo(1);
        assertThat(pagina.content().get(0).ativo()).isEqualTo("Duplicata Mercantil B");
        assertThat(pagina.content().get(0).cotacaoCambio()).isEqualByComparingTo("5.00");
    }

    @Test
    void combinaFiltros() {
        PaginaResultado<ExtratoLiquidacaoItem> pagina = adapter.buscar(
                new FiltroExtratoLiquidacao(T1.minusDays(1), T2.plusDays(1), "duplicata", Moeda.BRL), 0, 20);

        assertThat(pagina.totalElements()).isEqualTo(1);
        assertThat(pagina.content().get(0).ativo()).isEqualTo("Duplicata Mercantil A");
    }

    @Test
    void paginacaoRespeitaPageESizeComContagemTotalCorreta() {
        PaginaResultado<ExtratoLiquidacaoItem> pagina1 = adapter.buscar(
                new FiltroExtratoLiquidacao(null, null, null, null), 0, 2);
        PaginaResultado<ExtratoLiquidacaoItem> pagina2 = adapter.buscar(
                new FiltroExtratoLiquidacao(null, null, null, null), 1, 2);

        assertThat(pagina1.content()).hasSize(2);
        assertThat(pagina1.totalElements()).isEqualTo(3);
        assertThat(pagina1.totalPages()).isEqualTo(2);
        assertThat(pagina2.content()).hasSize(1);
        assertThat(pagina2.totalElements()).isEqualTo(3);
    }

    @Test
    void naoRetornaNadaQuandoFiltroNaoBateComNenhumRegistro() {
        PaginaResultado<ExtratoLiquidacaoItem> pagina = adapter.buscar(
                new FiltroExtratoLiquidacao(null, null, "inexistente", null), 0, 20);

        assertThat(pagina.content()).isEmpty();
        assertThat(pagina.totalElements()).isZero();
    }
}
