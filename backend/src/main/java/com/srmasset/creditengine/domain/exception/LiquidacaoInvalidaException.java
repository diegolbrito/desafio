package com.srmasset.creditengine.domain.exception;

/** Tentativa de liquidar um recebivel que nao esta (nem ja esteve) PRECIFICADO. */
public class LiquidacaoInvalidaException extends DomainException {

    public LiquidacaoInvalidaException(String message) {
        super(message);
    }
}
