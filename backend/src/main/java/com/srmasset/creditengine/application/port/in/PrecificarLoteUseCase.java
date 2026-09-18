package com.srmasset.creditengine.application.port.in;

import com.srmasset.creditengine.domain.LoteRecebiveis;

public interface PrecificarLoteUseCase {

    LoteRecebiveis precificar(ComandoPrecificarLote comando);
}
