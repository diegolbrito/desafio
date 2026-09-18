package com.srmasset.creditengine.application.service;

import com.srmasset.creditengine.application.exception.ParametroInvalidoException;
import com.srmasset.creditengine.application.port.in.ListarLotesRecebiveisUseCase;
import com.srmasset.creditengine.application.port.out.DirecaoOrdenacao;
import com.srmasset.creditengine.application.port.out.ListarLotesRecebiveisPort;
import com.srmasset.creditengine.application.port.out.LoteRecebiveisResumo;
import com.srmasset.creditengine.application.port.out.PaginaResultado;

import java.util.Set;

public class ListarLotesRecebiveisService implements ListarLotesRecebiveisUseCase {

    private static final Set<String> CAMPOS_ORDENACAO_PERMITIDOS = Set.of("createdAt", "dataReferencia", "status");

    private final ListarLotesRecebiveisPort listarLotesPort;

    public ListarLotesRecebiveisService(ListarLotesRecebiveisPort listarLotesPort) {
        this.listarLotesPort = listarLotesPort;
    }

    @Override
    public PaginaResultado<LoteRecebiveisResumo> listar(int page, int size, String campoOrdenacao, DirecaoOrdenacao direcao) {
        if (!CAMPOS_ORDENACAO_PERMITIDOS.contains(campoOrdenacao)) {
            throw new ParametroInvalidoException(
                    "Campo de ordenacao invalido: '%s'. Permitidos: %s".formatted(campoOrdenacao, CAMPOS_ORDENACAO_PERMITIDOS));
        }
        return listarLotesPort.listar(page, size, campoOrdenacao, direcao);
    }
}
