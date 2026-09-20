package com.srmasset.creditengine.adapter.out.http;

import com.srmasset.creditengine.application.metrics.CreditEngineMetrics;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testa a escada de resiliencia do adapter (ver SPEC.md "Premissas adotadas"
 * item 11) contra um servidor HTTP de verdade (JDK {@link HttpServer}, sem
 * dependencia de teste nova) cujo comportamento e' trocado em tempo de
 * execucao - mais fiel que mockar o cliente HTTP, principalmente pra testar
 * o circuit breaker (que depende de falhas de rede reais/timeout).
 */
class CotacaoCambioHttpAdapterTest {

    private static final BigDecimal FALLBACK = new BigDecimal("5.4321");

    private enum Comportamento { SUCESSO, ERRO_500, JSON_INVALIDO }

    private HttpServer server;
    private int port;
    private final AtomicReference<Comportamento> comportamento = new AtomicReference<>(Comportamento.SUCESSO);
    private final AtomicInteger totalRequisicoes = new AtomicInteger();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final RelogioMutavel relogio = new RelogioMutavel(Instant.parse("2026-09-20T12:00:00Z"));

    @BeforeEach
    void iniciarServidor() throws IOException {
        totalRequisicoes.set(0);
        comportamento.set(Comportamento.SUCESSO);
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        server.createContext("/api/v1/cotacoes/USD-BRL", exchange -> {
            totalRequisicoes.incrementAndGet();
            byte[] corpo;
            int status;
            switch (comportamento.get()) {
                case ERRO_500 -> {
                    status = 500;
                    corpo = "erro interno".getBytes(StandardCharsets.UTF_8);
                }
                case JSON_INVALIDO -> {
                    status = 200;
                    corpo = "{isso nao e json".getBytes(StandardCharsets.UTF_8);
                }
                default -> {
                    status = 200;
                    corpo = """
                            {"parMoedas":"USD-BRL","cotacao":"5.4321","atualizadoEm":"2026-09-20T12:00:00Z"}
                            """.getBytes(StandardCharsets.UTF_8);
                }
            }
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, corpo.length);
            exchange.getResponseBody().write(corpo);
            exchange.close();
        });
        server.start();
        port = server.getAddress().getPort();
    }

    @AfterEach
    void pararServidor() {
        server.stop(0);
    }

    private CotacaoCambioHttpAdapter criarAdapter(String urlBase, int limiteFalhas) {
        return new CotacaoCambioHttpAdapter(urlBase, Duration.ofMillis(500), Duration.ofMillis(500),
                FALLBACK, limiteFalhas, Duration.ofSeconds(30), relogio, new CreditEngineMetrics(meterRegistry));
    }

    @Test
    void buscaCotacaoComSucessoNoServicoExterno() {
        CotacaoCambioHttpAdapter adapter = criarAdapter("http://localhost:" + port, 3);

        BigDecimal cotacao = adapter.buscarCotacao();

        assertThat(cotacao).isEqualByComparingTo("5.4321");
        assertThat(meterRegistry.counter("creditengine.cotacao.consultas", "origem", "externa").count())
                .isEqualTo(1.0);
    }

    @Test
    void usaFallbackEstaticoQuandoServicoRetorna500ENuncaTeveCacheAntes() {
        comportamento.set(Comportamento.ERRO_500);
        CotacaoCambioHttpAdapter adapter = criarAdapter("http://localhost:" + port, 3);

        BigDecimal cotacao = adapter.buscarCotacao();

        assertThat(cotacao).isEqualByComparingTo(FALLBACK);
        assertThat(meterRegistry.counter("creditengine.cotacao.consultas", "origem", "fallback_estatico").count())
                .isEqualTo(1.0);
    }

    @Test
    void usaFallbackEstaticoQuandoRespostaEhJsonInvalido() {
        comportamento.set(Comportamento.JSON_INVALIDO);
        CotacaoCambioHttpAdapter adapter = criarAdapter("http://localhost:" + port, 3);

        BigDecimal cotacao = adapter.buscarCotacao();

        assertThat(cotacao).isEqualByComparingTo(FALLBACK);
    }

    @Test
    void usaUltimaCotacaoConhecidaQuandoServicoFalhaAposUmSucessoAnterior() {
        CotacaoCambioHttpAdapter adapter = criarAdapter("http://localhost:" + port, 3);
        BigDecimal primeiraCotacao = adapter.buscarCotacao();
        assertThat(primeiraCotacao).isEqualByComparingTo("5.4321");

        comportamento.set(Comportamento.ERRO_500);
        BigDecimal segundaCotacao = adapter.buscarCotacao();

        assertThat(segundaCotacao).isEqualByComparingTo(primeiraCotacao);
        assertThat(meterRegistry.counter("creditengine.cotacao.consultas", "origem", "cache").count())
                .isEqualTo(1.0);
    }

    @Test
    void abreCircuitoAposLimiteDeFalhasEPulaChamadaDeRedeAteAJanelaPassar() {
        comportamento.set(Comportamento.ERRO_500);
        CotacaoCambioHttpAdapter adapter = criarAdapter("http://localhost:" + port, 2);

        adapter.buscarCotacao(); // falha 1
        adapter.buscarCotacao(); // falha 2 - atinge o limite, circuito abre
        assertThat(totalRequisicoes.get()).isEqualTo(2);

        // circuito aberto: chamadas adicionais nao devem gerar nenhuma requisicao HTTP nova
        adapter.buscarCotacao();
        adapter.buscarCotacao();
        assertThat(totalRequisicoes.get()).isEqualTo(2);

        relogio.avancar(Duration.ofSeconds(31));
        comportamento.set(Comportamento.SUCESSO);

        // janela do circuito passou: adapter volta a tentar a rede
        BigDecimal cotacao = adapter.buscarCotacao();
        assertThat(cotacao).isEqualByComparingTo("5.4321");
        assertThat(totalRequisicoes.get()).isEqualTo(3);
    }

    @Test
    void naoAbreCircuitoAntesDoLimiteDeFalhasConfigurado() {
        comportamento.set(Comportamento.ERRO_500);
        CotacaoCambioHttpAdapter adapter = criarAdapter("http://localhost:" + port, 3);

        adapter.buscarCotacao();
        adapter.buscarCotacao();
        assertThat(totalRequisicoes.get()).isEqualTo(2); // ainda tentou a rede nas duas vezes

        comportamento.set(Comportamento.SUCESSO);
        BigDecimal cotacao = adapter.buscarCotacao();

        assertThat(cotacao).isEqualByComparingTo("5.4321");
        assertThat(totalRequisicoes.get()).isEqualTo(3); // tentou a rede de novo (circuito nao abriu)
    }

    private static final class RelogioMutavel extends Clock {
        private Instant instante;

        RelogioMutavel(Instant inicial) {
            this.instante = inicial;
        }

        void avancar(Duration duracao) {
            instante = instante.plus(duracao);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instante;
        }
    }
}
