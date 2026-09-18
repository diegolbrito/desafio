package com.srmasset.creditengine.domain.exception;

/**
 * Data de vencimento do recebivel nao permite calcular um prazo valido para precificacao
 * (ex.: vencimento no passado ou na propria data de referencia). Sinaliza rejeicao do item,
 * nao aborta o lote inteiro (ver SPEC.md, "Premissas adotadas" - item 5).
 */
public class PrazoInvalidoException extends DomainException {

    public PrazoInvalidoException(String message) {
        super(message);
    }
}
