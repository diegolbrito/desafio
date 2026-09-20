package com.srmasset.creditengine.application.port.in;

import com.srmasset.creditengine.domain.Recebivel;

import java.util.Optional;
import java.util.UUID;

public interface LiquidarRecebivelUseCase {

    /**
     * @return vazio se o lote/recebivel nao existir (ou o recebivel nao pertencer
     *         ao lote informado); presente com o recebivel resultante caso contrario
     *         - tanto na liquidacao efetiva quanto na chamada idempotente repetida.
     */
    Optional<Recebivel> liquidar(UUID loteId, UUID recebivelId);
}
