package com.srmasset.creditengine.application.exception;

/** Parametro de entrada de um caso de uso fora do conjunto permitido (ex.: campo de ordenacao). */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String message) {
        super(message);
    }
}
