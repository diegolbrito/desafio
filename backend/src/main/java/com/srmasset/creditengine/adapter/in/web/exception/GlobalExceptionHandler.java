package com.srmasset.creditengine.adapter.in.web.exception;

import com.srmasset.creditengine.application.exception.ParametroInvalidoException;
import com.srmasset.creditengine.domain.exception.DomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Mapeia excecoes para respostas RFC 9457 (Problem Details), conforme
 * SPEC.md "API (REST)". Mensagens em portugues; o correlationId ja esta no
 * MDC (ver CorrelationIdFilter) e portanto aparece nos logs automaticamente.
 *
 * <p>Ponto central de log de erro (ver SPEC.md > "Logging"): 4xx (validacao,
 * regra de negocio, nao encontrado, conflito) em WARN sem stacktrace - sao
 * erros esperados/do cliente, nao falhas do sistema; 500 em ERROR com a
 * excecao completa. Cada erro e' logado uma unica vez, aqui - as camadas
 * inferiores nao devem logar e relancar.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail tratarValidacao(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Um ou mais campos do payload sao invalidos");
        problem.setTitle("Erro de validacao");
        problem.setInstance(URI.create(request.getRequestURI()));

        List<Map<String, String>> erros = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", String.valueOf(fe.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", erros);

        log.warn("Validacao falhou: {} {} - {} campo(s) invalido(s)",
                request.getMethod(), request.getRequestURI(), erros.size());
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail tratarJsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Corpo da requisicao ausente ou mal formado");
        problem.setTitle("Requisicao invalida");
        problem.setInstance(URI.create(request.getRequestURI()));

        log.warn("JSON invalido: {} {}", request.getMethod(), request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    public ProblemDetail tratarParametroInvalido(ParametroInvalidoException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Parametro invalido");
        problem.setInstance(URI.create(request.getRequestURI()));

        log.warn("Parametro invalido: {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail tratarTipoDeParametroInvalido(MethodArgumentTypeMismatchException ex,
                                                         HttpServletRequest request) {
        String detalhe = "Parametro '%s' possui valor invalido: %s".formatted(ex.getName(), ex.getValue());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detalhe);
        problem.setTitle("Parametro invalido");
        problem.setInstance(URI.create(request.getRequestURI()));

        log.warn("Parametro invalido: {} {} - {}", request.getMethod(), request.getRequestURI(), detalhe);
        return problem;
    }

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail tratarNaoEncontrado(RecursoNaoEncontradoException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Recurso nao encontrado");
        problem.setInstance(URI.create(request.getRequestURI()));

        log.warn("Recurso nao encontrado: {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return problem;
    }

    @ExceptionHandler(DomainException.class)
    public ProblemDetail tratarRegraDeNegocio(DomainException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_CONTENT, ex.getMessage());
        problem.setTitle("Regra de negocio violada");
        problem.setInstance(URI.create(request.getRequestURI()));

        log.warn("Regra de negocio violada: {} {} - {}", request.getMethod(), request.getRequestURI(), ex.getMessage());
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail tratarConflitoVersao(OptimisticLockingFailureException ex, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "O recurso foi modificado por outra operacao. Recarregue e tente novamente.");
        problem.setTitle("Conflito de versao");
        problem.setInstance(URI.create(request.getRequestURI()));

        log.warn("Conflito de versao (optimistic locking): {} {}", request.getMethod(), request.getRequestURI());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail tratarErroInesperado(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado ao processar {} {}", request.getMethod(), request.getRequestURI(), ex);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Erro interno do servidor");
        problem.setTitle("Erro interno");
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
