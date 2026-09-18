package com.srmasset.creditengine.application.port.in;

import com.srmasset.creditengine.application.port.out.DirecaoOrdenacao;
import com.srmasset.creditengine.application.port.out.LoteRecebiveisResumo;
import com.srmasset.creditengine.application.port.out.PaginaResultado;

public interface ListarLotesRecebiveisUseCase {

    PaginaResultado<LoteRecebiveisResumo> listar(int page, int size, String campoOrdenacao, DirecaoOrdenacao direcao);
}
