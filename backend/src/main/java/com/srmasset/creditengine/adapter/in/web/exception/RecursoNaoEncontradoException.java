package com.srmasset.creditengine.adapter.in.web.exception;

/** Recurso solicitado via path variable (ex.: GET /{id}) nao existe. Mapeada para 404. */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String message) {
        super(message);
    }
}
