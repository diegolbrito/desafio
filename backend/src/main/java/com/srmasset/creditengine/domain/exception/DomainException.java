package com.srmasset.creditengine.domain.exception;

/**
 * Raiz das excecoes que representam violacao de regra de negocio do dominio.
 * Adapters de entrada mapeiam subclasses desta excecao para respostas HTTP.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
