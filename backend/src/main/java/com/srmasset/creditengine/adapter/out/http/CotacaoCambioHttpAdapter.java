package com.srmasset.creditengine.adapter.out.http;

import com.srmasset.creditengine.application.metrics.CreditEngineMetrics;
import com.srmasset.creditengine.application.port.out.CotacaoCambioPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Busca a cotacao de cambio (BRL por 1 USD) num servico HTTP externo, com uma
 * escada de resiliencia para quando ele estiver fora do ar (ver SPEC.md,
 * "Premissas adotadas" item 11): nenhuma falha de rede vaza como excecao para
 * quem chama {@link #buscarCotacao()} - sempre ha um valor utilizavel.
 *
 * <p>Classe plain (sem anotacao Spring), como os demais services da camada de
 * aplicacao: os valores de configuracao (URL, timeouts, fallback, limites do
 * circuit breaker) sao resolvidos via {@code @Value} na camada de composicao
 * (UseCaseConfig), nunca aqui dentro.
 *
 * <p>Escada de resolucao a cada chamada:
 * <ol>
 *   <li>Circuito aberto (muitas falhas consecutivas recentes)? Pula a chamada de
 *       rede e vai direto para o fallback.</li>
 *   <li>Senao, tenta a chamada HTTP (timeout curto). Sucesso: atualiza o cache do
 *       ultimo valor bom, fecha o circuito, retorna o valor.</li>
 *   <li>Falha (timeout, conexao recusada, HTTP nao-2xx, corpo malformado): conta
 *       como falha (pode abrir o circuito) e cai no fallback.</li>
 *   <li>Fallback: usa o ultimo valor bom em cache, se existir; senao usa o valor
 *       estatico de seguranca da configuracao.</li>
 * </ol>
 */
public class CotacaoCambioHttpAdapter implements CotacaoCambioPort {

    private static final Logger log = LoggerFactory.getLogger(CotacaoCambioHttpAdapter.class);
    private static final String PATH_COTACAO_USD_BRL = "/api/v1/cotacoes/USD-BRL";

    private final RestClient restClient;
    private final BigDecimal cotacaoFallback;
    private final int limiteFalhasConsecutivas;
    private final Duration janelaCircuitoAberto;
    private final Clock clock;
    private final CreditEngineMetrics metrics;

    private final AtomicReference<CotacaoCache> ultimaCotacaoConhecida = new AtomicReference<>();
    private final AtomicInteger falhasConsecutivas = new AtomicInteger(0);
    private final AtomicReference<Instant> circuitoAbertoAte = new AtomicReference<>();

    public CotacaoCambioHttpAdapter(String serviceUrl, Duration timeoutConexao, Duration timeoutLeitura,
                                     BigDecimal cotacaoFallback, int limiteFalhasConsecutivas,
                                     Duration janelaCircuitoAberto, Clock clock, CreditEngineMetrics metrics) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) timeoutConexao.toMillis());
        requestFactory.setReadTimeout((int) timeoutLeitura.toMillis());
        this.restClient = RestClient.builder().baseUrl(serviceUrl).requestFactory(requestFactory).build();
        this.cotacaoFallback = cotacaoFallback;
        this.limiteFalhasConsecutivas = limiteFalhasConsecutivas;
        this.janelaCircuitoAberto = janelaCircuitoAberto;
        this.clock = clock;
        this.metrics = metrics;
    }

    @Override
    public BigDecimal buscarCotacao() {
        if (circuitoEstaAberto()) {
            log.warn("Circuito aberto para o servico de cotacao cambial - pulando chamada de rede");
            return resolverFallback();
        }

        try {
            CotacaoCambioResponse resposta = restClient.get()
                    .uri(PATH_COTACAO_USD_BRL)
                    .retrieve()
                    .body(CotacaoCambioResponse.class);
            if (resposta == null || resposta.cotacao() == null) {
                throw new IllegalStateException("Resposta do servico de cotacao cambial sem o campo 'cotacao'");
            }
            registrarSucesso(resposta.cotacao());
            metrics.registrarCotacaoOrigem("externa");
            return resposta.cotacao();
        } catch (RestClientException | IllegalStateException e) {
            log.warn("Falha ao buscar cotacao no servico externo: {}", e.getMessage());
            registrarFalha();
            return resolverFallback();
        }
    }

    private BigDecimal resolverFallback() {
        CotacaoCache cache = ultimaCotacaoConhecida.get();
        if (cache != null) {
            log.warn("Usando ultima cotacao cambial conhecida ({}), obtida em {}", cache.valor(), cache.obtidaEm());
            metrics.registrarCotacaoOrigem("cache");
            return cache.valor();
        }
        log.warn("Usando cotacao cambial de fallback estatica ({}) - nenhum valor em cache ainda", cotacaoFallback);
        metrics.registrarCotacaoOrigem("fallback_estatico");
        return cotacaoFallback;
    }

    private boolean circuitoEstaAberto() {
        Instant abertoAte = circuitoAbertoAte.get();
        return abertoAte != null && Instant.now(clock).isBefore(abertoAte);
    }

    private void registrarSucesso(BigDecimal cotacao) {
        falhasConsecutivas.set(0);
        circuitoAbertoAte.set(null);
        ultimaCotacaoConhecida.set(new CotacaoCache(cotacao, OffsetDateTime.now(clock)));
    }

    private void registrarFalha() {
        int falhas = falhasConsecutivas.incrementAndGet();
        if (falhas >= limiteFalhasConsecutivas) {
            circuitoAbertoAte.set(Instant.now(clock).plus(janelaCircuitoAberto));
            log.warn("Circuito aberto para o servico de cotacao cambial: {} falhas consecutivas", falhas);
        }
    }

    private record CotacaoCache(BigDecimal valor, OffsetDateTime obtidaEm) {
    }

    private record CotacaoCambioResponse(String parMoedas, BigDecimal cotacao, String atualizadoEm) {
    }
}
