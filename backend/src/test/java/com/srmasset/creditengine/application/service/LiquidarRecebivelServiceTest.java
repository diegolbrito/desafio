package com.srmasset.creditengine.application.service;

import com.srmasset.creditengine.application.port.out.LiquidarRecebivelPort;
import com.srmasset.creditengine.application.port.out.RegistrarEventoTransacaoPort;
import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.EventoTransacao;
import com.srmasset.creditengine.domain.Recebivel;
import com.srmasset.creditengine.domain.ResultadoDesagio;
import com.srmasset.creditengine.domain.StatusRecebivel;
import com.srmasset.creditengine.domain.TipoEventoTransacao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LiquidarRecebivelServiceTest {

    private static final Clock RELOGIO_FIXO = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);

    @Mock
    private LiquidarRecebivelPort liquidarRecebivelPort;
    @Mock
    private RegistrarEventoTransacaoPort registrarEventoPort;

    private LiquidarRecebivelService service;

    @BeforeEach
    void setUp() {
        service = new LiquidarRecebivelService(liquidarRecebivelPort, registrarEventoPort, RELOGIO_FIXO);
    }

    private Recebivel recebivelPrecificado() {
        Recebivel recebivel = Recebivel.criar("Ativo Teste", new BigDecimal("1000.00"),
                LocalDate.of(2026, 12, 31), CategoriaRisco.B);
        recebivel.atribuirId(UUID.randomUUID());
        recebivel.aplicarPrecificacao(new ResultadoDesagio(
                new BigDecimal("950.00"), new BigDecimal("50.00"), new BigDecimal("0.105000")));
        return recebivel;
    }

    @Test
    void retornaVazioQuandoRecebivelNaoEncontrado() {
        UUID loteId = UUID.randomUUID();
        UUID recebivelId = UUID.randomUUID();
        when(liquidarRecebivelPort.buscarParaLiquidar(loteId, recebivelId)).thenReturn(Optional.empty());

        Optional<Recebivel> resultado = service.liquidar(loteId, recebivelId);

        assertThat(resultado).isEmpty();
        verify(registrarEventoPort, never()).registrar(any());
        verify(liquidarRecebivelPort, never()).salvar(any());
    }

    @Test
    void liquidaERegistraEventoDeAuditoriaNaPrimeiraChamada() {
        UUID loteId = UUID.randomUUID();
        Recebivel recebivel = recebivelPrecificado();
        when(liquidarRecebivelPort.buscarParaLiquidar(loteId, recebivel.getId())).thenReturn(Optional.of(recebivel));

        Optional<Recebivel> resultado = service.liquidar(loteId, recebivel.getId());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getStatus()).isEqualTo(StatusRecebivel.LIQUIDADO);
        verify(liquidarRecebivelPort, times(1)).salvar(recebivel);

        ArgumentCaptor<EventoTransacao> eventoCaptor = ArgumentCaptor.forClass(EventoTransacao.class);
        verify(registrarEventoPort, times(1)).registrar(eventoCaptor.capture());
        assertThat(eventoCaptor.getValue().tipo()).isEqualTo(TipoEventoTransacao.RECEBIVEL_LIQUIDADO);
        assertThat(eventoCaptor.getValue().loteId()).isEqualTo(loteId);
        assertThat(eventoCaptor.getValue().recebivelId()).isEqualTo(recebivel.getId());
    }

    @Test
    void chamadaRepetidaParaRecebivelJaLiquidadoNaoRegistraNovoEvento() {
        UUID loteId = UUID.randomUUID();
        Recebivel recebivel = recebivelPrecificado();
        recebivel.liquidar(java.time.OffsetDateTime.now(RELOGIO_FIXO));
        when(liquidarRecebivelPort.buscarParaLiquidar(loteId, recebivel.getId())).thenReturn(Optional.of(recebivel));

        Optional<Recebivel> resultado = service.liquidar(loteId, recebivel.getId());

        assertThat(resultado).isPresent();
        assertThat(resultado.get().getStatus()).isEqualTo(StatusRecebivel.LIQUIDADO);
        verify(registrarEventoPort, never()).registrar(any());
        // salvar() ainda e' chamado (idempotente/sem efeito no banco via dirty-checking), mas sem novo evento
        verify(liquidarRecebivelPort, times(1)).salvar(recebivel);
    }
}
