package com.srmasset.creditengine.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.UUID;

/**
 * Gera X-Correlation-Id quando ausente, propaga na resposta e no MDC (logs)
 * durante o processamento da requisicao, e loga metodo/rota/status/duracao de
 * cada request em INFO - unico ponto de log de requisicao da aplicacao, para
 * nao repetir isso em cada controller (ver SPEC.md, secao "Logging"). O MDC e'
 * sempre limpo no finally, inclusive se a aplicacao vier a rodar em virtual
 * threads no futuro (spring.threads.virtual.enabled ainda nao esta ativo).
 */
@Component
public class CorrelationIdFilter extends HttpFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }

        response.setHeader(HEADER, correlationId);
        MDC.put(MDC_KEY, correlationId);
        long inicio = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long duracaoMs = (System.nanoTime() - inicio) / 1_000_000;
            log.info("{} {} -> {} ({} ms)", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), duracaoMs);
            MDC.remove(MDC_KEY);
        }
    }
}
