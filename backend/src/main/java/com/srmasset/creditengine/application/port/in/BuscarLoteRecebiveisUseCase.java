package com.srmasset.creditengine.application.port.in;

import com.srmasset.creditengine.application.port.out.LoteRecebiveisDetalhe;

import java.util.Optional;
import java.util.UUID;

public interface BuscarLoteRecebiveisUseCase {

    Optional<LoteRecebiveisDetalhe> buscarPorId(UUID id);
}
