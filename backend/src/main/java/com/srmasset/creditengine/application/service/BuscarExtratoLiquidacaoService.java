package com.srmasset.creditengine.application.service;

import com.srmasset.creditengine.application.port.in.BuscarExtratoLiquidacaoUseCase;
import com.srmasset.creditengine.application.port.out.BuscarExtratoLiquidacaoPort;
import com.srmasset.creditengine.application.port.out.ExtratoLiquidacaoItem;
import com.srmasset.creditengine.application.port.out.FiltroExtratoLiquidacao;
import com.srmasset.creditengine.application.port.out.PaginaResultado;

/** So' delega para a port - e' uma consulta (relatorio), nao um comando com regra de negocio propria. */
public class BuscarExtratoLiquidacaoService implements BuscarExtratoLiquidacaoUseCase {

    private final BuscarExtratoLiquidacaoPort port;

    public BuscarExtratoLiquidacaoService(BuscarExtratoLiquidacaoPort port) {
        this.port = port;
    }

    @Override
    public PaginaResultado<ExtratoLiquidacaoItem> buscar(FiltroExtratoLiquidacao filtro, int page, int size) {
        return port.buscar(filtro, page, size);
    }
}
