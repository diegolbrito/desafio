package com.srmasset.creditengine.application.port.out;

import com.srmasset.creditengine.domain.Recebivel;

import java.util.Optional;
import java.util.UUID;

public interface LiquidarRecebivelPort {

    /**
     * Busca o recebivel com bloqueio pessimista (SELECT ... FOR UPDATE), para
     * serializar chamadas concorrentes de liquidacao do mesmo recebivel (ver
     * SPEC.md "Premissas adotadas" - liquidacao - sobre por que optamos por
     * bloqueio pessimista em vez do @Version otimista ja usado nas demais escritas).
     */
    Optional<Recebivel> buscarParaLiquidar(UUID loteId, UUID recebivelId);

    void salvar(Recebivel recebivel);
}
