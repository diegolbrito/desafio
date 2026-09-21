package com.srmasset.creditengine.application.port.out;

public interface BuscarExtratoLiquidacaoPort {

    PaginaResultado<ExtratoLiquidacaoItem> buscar(FiltroExtratoLiquidacao filtro, int page, int size);
}
