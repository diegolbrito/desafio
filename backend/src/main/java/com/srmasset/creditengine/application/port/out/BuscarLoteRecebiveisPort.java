package com.srmasset.creditengine.application.port.out;

import java.util.Optional;
import java.util.UUID;

public interface BuscarLoteRecebiveisPort {

    Optional<LoteRecebiveisDetalhe> buscarPorId(UUID id);
}
