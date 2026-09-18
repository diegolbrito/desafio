package com.srmasset.creditengine.domain.exception;

/** Violacao de invariante estrutural de um LoteRecebiveis. */
public class LoteRecebiveisInvalidoException extends DomainException {

    public LoteRecebiveisInvalidoException(String message) {
        super(message);
    }
}
