package com.srmasset.creditengine.domain.exception;

/** Violacao de invariante estrutural de um Recebivel (dados obrigatorios ausentes ou inconsistentes). */
public class RecebivelInvalidoException extends DomainException {

    public RecebivelInvalidoException(String message) {
        super(message);
    }
}
