package com.srmasset.creditengine.adapter.out.persistence;

import com.srmasset.creditengine.adapter.out.persistence.entity.TransacaoEventoEntity;
import com.srmasset.creditengine.adapter.out.persistence.repository.TransacaoEventoJpaRepository;
import com.srmasset.creditengine.application.port.out.RegistrarEventoTransacaoPort;
import com.srmasset.creditengine.domain.EventoTransacao;
import org.springframework.stereotype.Component;

@Component
public class TransacaoEventoPersistenceAdapter implements RegistrarEventoTransacaoPort {

    private final TransacaoEventoJpaRepository repository;

    public TransacaoEventoPersistenceAdapter(TransacaoEventoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void registrar(EventoTransacao evento) {
        repository.save(new TransacaoEventoEntity(
                evento.loteId(), evento.recebivelId(), evento.tipo(), evento.descricao(), evento.ocorridoEm()));
    }
}
