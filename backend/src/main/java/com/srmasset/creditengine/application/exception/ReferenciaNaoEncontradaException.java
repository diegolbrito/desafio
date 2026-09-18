package com.srmasset.creditengine.application.exception;

/**
 * Dado de referencia (taxa base, categoria de risco) ausente na configuracao do sistema.
 * Representa uma falha sistemica/operacional, nao uma regra de negocio do dominio.
 */
public class ReferenciaNaoEncontradaException extends RuntimeException {

    public ReferenciaNaoEncontradaException(String message) {
        super(message);
    }
}
