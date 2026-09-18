package com.srmasset.creditengine.application.service;

import com.srmasset.creditengine.application.port.in.BuscarLoteRecebiveisUseCase;
import com.srmasset.creditengine.application.port.out.BuscarLoteRecebiveisPort;
import com.srmasset.creditengine.application.port.out.LoteRecebiveisDetalhe;

import java.util.Optional;
import java.util.UUID;

public class BuscarLoteRecebiveisService implements BuscarLoteRecebiveisUseCase {

    private final BuscarLoteRecebiveisPort buscarLotePort;

    public BuscarLoteRecebiveisService(BuscarLoteRecebiveisPort buscarLotePort) {
        this.buscarLotePort = buscarLotePort;
    }

    @Override
    public Optional<LoteRecebiveisDetalhe> buscarPorId(UUID id) {
        return buscarLotePort.buscarPorId(id);
    }
}
