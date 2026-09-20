package com.srmasset.creditengine.application.service;

import com.srmasset.creditengine.application.exception.ReferenciaNaoEncontradaException;
import com.srmasset.creditengine.application.port.in.ComandoPrecificarLote;
import com.srmasset.creditengine.application.port.out.CategoriaRiscoRepositoryPort;
import com.srmasset.creditengine.application.port.out.RegistrarEventoTransacaoPort;
import com.srmasset.creditengine.application.port.out.SalvarLoteRecebiveisPort;
import com.srmasset.creditengine.application.port.out.TaxaBaseRepositoryPort;
import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.LoteRecebiveis;
import com.srmasset.creditengine.domain.Moeda;
import com.srmasset.creditengine.domain.StatusLote;
import com.srmasset.creditengine.domain.StatusRecebivel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrecificarLoteServiceTest {

    private static final Clock RELOGIO_FIXO = Clock.fixed(Instant.parse("2026-09-18T00:00:00Z"), ZoneOffset.UTC);

    @Mock
    private TaxaBaseRepositoryPort taxaBaseRepository;
    @Mock
    private CategoriaRiscoRepositoryPort categoriaRiscoRepository;
    @Mock
    private SalvarLoteRecebiveisPort salvarLotePort;
    @Mock
    private RegistrarEventoTransacaoPort registrarEventoPort;

    private PrecificarLoteService service;

    @BeforeEach
    void setUp() {
        service = new PrecificarLoteService(taxaBaseRepository, categoriaRiscoRepository,
                salvarLotePort, registrarEventoPort, new BigDecimal("0.005"), new BigDecimal("5.20"), RELOGIO_FIXO);

        when(salvarLotePort.salvar(any())).thenAnswer(invocation -> {
            LoteRecebiveis lote = invocation.getArgument(0);
            lote.atribuirId(UUID.randomUUID());
            lote.getRecebiveis().forEach(r -> r.atribuirId(UUID.randomUUID()));
            return lote;
        });
    }

    @Test
    void precificaTodosOsItensQuandoDadosSaoValidos() {
        when(taxaBaseRepository.buscarTaxaVigente(Moeda.BRL)).thenReturn(new BigDecimal("0.1065"));
        when(categoriaRiscoRepository.buscarSpread(CategoriaRisco.B)).thenReturn(new BigDecimal("0.035"));

        ComandoPrecificarLote comando = new ComandoPrecificarLote(List.of(
                new ComandoPrecificarLote.ComandoRecebivel("Ativo A", new BigDecimal("1000.00"),
                        LocalDate.of(2026, 12, 31), CategoriaRisco.B, null),
                new ComandoPrecificarLote.ComandoRecebivel("Ativo B", new BigDecimal("2000.00"),
                        LocalDate.of(2027, 1, 15), CategoriaRisco.B, null)
        ));

        LoteRecebiveis lote = service.precificar(comando);

        assertThat(lote.getStatus()).isEqualTo(StatusLote.PRECIFICADO);
        assertThat(lote.getRecebiveis()).allMatch(r -> r.getStatus() == StatusRecebivel.PRECIFICADO);
        verify(salvarLotePort).salvar(any());
        // 1 lote_recebido + 2 recebiveis precificados + 1 lote_precificado
        verify(registrarEventoPort, times(4)).registrar(any());
    }

    @Test
    void rejeitaApenasOItemComVencimentoInvalidoSemAbortarOLote() {
        when(taxaBaseRepository.buscarTaxaVigente(Moeda.BRL)).thenReturn(new BigDecimal("0.1065"));
        when(categoriaRiscoRepository.buscarSpread(CategoriaRisco.B)).thenReturn(new BigDecimal("0.035"));

        ComandoPrecificarLote comando = new ComandoPrecificarLote(List.of(
                new ComandoPrecificarLote.ComandoRecebivel("Ativo A", new BigDecimal("1000.00"),
                        LocalDate.of(2026, 9, 18), CategoriaRisco.B, null), // vencimento == dataReferencia
                new ComandoPrecificarLote.ComandoRecebivel("Ativo B", new BigDecimal("2000.00"),
                        LocalDate.of(2027, 1, 15), CategoriaRisco.B, null)
        ));

        LoteRecebiveis lote = service.precificar(comando);

        assertThat(lote.getStatus()).isEqualTo(StatusLote.PRECIFICADO);
        assertThat(lote.getRecebiveis().get(0).getStatus()).isEqualTo(StatusRecebivel.REJEITADO);
        assertThat(lote.getRecebiveis().get(0).getMotivoRejeicao()).isNotBlank();
        assertThat(lote.getRecebiveis().get(1).getStatus()).isEqualTo(StatusRecebivel.PRECIFICADO);
    }

    @Test
    void marcaLoteComErroQuandoTaxaBaseNaoEncontrada() {
        // ativo e' sempre BRL (ver SPEC.md item 3); esse cenario simula a referencia de taxa
        // base ausente mesmo assim (ex.: seed removido do banco), nao mais escolha de moeda.
        when(taxaBaseRepository.buscarTaxaVigente(Moeda.BRL))
                .thenThrow(new ReferenciaNaoEncontradaException("Taxa base nao configurada para BRL"));

        ComandoPrecificarLote comando = new ComandoPrecificarLote(List.of(
                new ComandoPrecificarLote.ComandoRecebivel("Ativo A", new BigDecimal("1000.00"),
                        LocalDate.of(2026, 12, 31), CategoriaRisco.B, null)
        ));

        LoteRecebiveis lote = service.precificar(comando);

        assertThat(lote.getStatus()).isEqualTo(StatusLote.ERRO);
        verify(salvarLotePort).salvar(any());
    }

    @Test
    void precificaCrossCurrencyConvertendoValoresParaAMoedaDePagamento() {
        when(taxaBaseRepository.buscarTaxaVigente(Moeda.BRL)).thenReturn(new BigDecimal("0.05"));
        when(categoriaRiscoRepository.buscarSpread(CategoriaRisco.B)).thenReturn(new BigDecimal("0.03"));
        service = new PrecificarLoteService(taxaBaseRepository, categoriaRiscoRepository,
                salvarLotePort, registrarEventoPort, new BigDecimal("0.02"), new BigDecimal("5.00"), RELOGIO_FIXO);

        ComandoPrecificarLote comando = new ComandoPrecificarLote(List.of(
                new ComandoPrecificarLote.ComandoRecebivel("Ativo A", new BigDecimal("11000.00"),
                        LocalDate.of(2026, 10, 18), CategoriaRisco.B, Moeda.USD)
        ));

        LoteRecebiveis lote = service.precificar(comando);

        // taxaDesconto = 0.10, prazoMeses = 1 -> valorPresente BRL = 11000/1.10 = 10000.00,
        // valorDesagio BRL = 1000.00. valorPresente convertido para USD (cotacao 5.00):
        // 10000/5 = 2000.00. valorDesagio permanece na moeda do titulo (BRL), sem conversao.
        var recebivel = lote.getRecebiveis().get(0);
        assertThat(recebivel.getStatus()).isEqualTo(StatusRecebivel.PRECIFICADO);
        assertThat(recebivel.getValorPresente()).isEqualByComparingTo("2000.00");
        assertThat(recebivel.getValorDesagio()).isEqualByComparingTo("1000.00");
        assertThat(recebivel.getMoedaPagamento()).isEqualTo(Moeda.USD);
        assertThat(recebivel.getCotacaoCambio()).isEqualByComparingTo("5.00");
    }
}
