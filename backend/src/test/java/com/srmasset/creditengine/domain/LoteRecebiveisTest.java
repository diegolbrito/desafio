package com.srmasset.creditengine.domain;

import com.srmasset.creditengine.domain.exception.LoteRecebiveisInvalidoException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LoteRecebiveisTest {

    private Recebivel recebivel() {
        return Recebivel.criar("Ativo Ltda", new BigDecimal("1000.00"),
                LocalDate.of(2026, 12, 31), CategoriaRisco.B);
    }

    @Test
    void criaLoteValidoComStatusRecebido() {
        LoteRecebiveis lote = LoteRecebiveis.criar(LocalDate.of(2026, 9, 18), List.of(recebivel()));

        assertThat(lote.getStatus()).isEqualTo(StatusLote.RECEBIDO);
        assertThat(lote.getRecebiveis()).hasSize(1);
    }

    @Test
    void rejeitaListaDeRecebiveisVazia() {
        assertThatThrownBy(() -> LoteRecebiveis.criar(LocalDate.of(2026, 9, 18), List.of()))
                .isInstanceOf(LoteRecebiveisInvalidoException.class);
    }

    @Test
    void rejeitaDataDeReferenciaNula() {
        assertThatThrownBy(() -> LoteRecebiveis.criar(null, List.of(recebivel())))
                .isInstanceOf(LoteRecebiveisInvalidoException.class);
    }

    @Test
    void marcarPrecificadoAtualizaStatus() {
        LoteRecebiveis lote = LoteRecebiveis.criar(LocalDate.of(2026, 9, 18), List.of(recebivel()));

        lote.marcarPrecificado();

        assertThat(lote.getStatus()).isEqualTo(StatusLote.PRECIFICADO);
    }

    @Test
    void marcarErroAtualizaStatus() {
        LoteRecebiveis lote = LoteRecebiveis.criar(LocalDate.of(2026, 9, 18), List.of(recebivel()));

        lote.marcarErro();

        assertThat(lote.getStatus()).isEqualTo(StatusLote.ERRO);
    }

    @Test
    void listaDeRecebiveisRetornadaEhImutavel() {
        LoteRecebiveis lote = LoteRecebiveis.criar(LocalDate.of(2026, 9, 18), List.of(recebivel()));

        assertThatThrownBy(() -> lote.getRecebiveis().add(recebivel()))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
