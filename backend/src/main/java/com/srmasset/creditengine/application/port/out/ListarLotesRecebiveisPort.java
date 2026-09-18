package com.srmasset.creditengine.application.port.out;

public interface ListarLotesRecebiveisPort {

    PaginaResultado<LoteRecebiveisResumo> listar(int page, int size, String campoOrdenacao, DirecaoOrdenacao direcao);
}
