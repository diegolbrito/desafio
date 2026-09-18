package com.srmasset.creditengine.application.port.out;

import com.srmasset.creditengine.domain.EventoTransacao;

public interface RegistrarEventoTransacaoPort {

    void registrar(EventoTransacao evento);
}
