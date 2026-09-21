package com.srmasset.creditengine.application.port.in;

import com.srmasset.creditengine.application.port.out.ExtratoLiquidacaoItem;
import com.srmasset.creditengine.application.port.out.FiltroExtratoLiquidacao;
import com.srmasset.creditengine.application.port.out.PaginaResultado;

public interface BuscarExtratoLiquidacaoUseCase {

    PaginaResultado<ExtratoLiquidacaoItem> buscar(FiltroExtratoLiquidacao filtro, int page, int size);
}
