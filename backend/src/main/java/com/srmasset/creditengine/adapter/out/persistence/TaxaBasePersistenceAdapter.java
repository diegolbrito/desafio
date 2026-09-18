package com.srmasset.creditengine.adapter.out.persistence;

import com.srmasset.creditengine.adapter.out.persistence.entity.TaxaBaseEntity;
import com.srmasset.creditengine.adapter.out.persistence.repository.TaxaBaseJpaRepository;
import com.srmasset.creditengine.application.exception.ReferenciaNaoEncontradaException;
import com.srmasset.creditengine.application.port.out.TaxaBaseRepositoryPort;
import com.srmasset.creditengine.domain.Moeda;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TaxaBasePersistenceAdapter implements TaxaBaseRepositoryPort {

    private final TaxaBaseJpaRepository repository;

    public TaxaBasePersistenceAdapter(TaxaBaseJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public BigDecimal buscarTaxaVigente(Moeda moeda) {
        return repository.findByMoeda(moeda)
                .map(TaxaBaseEntity::getTaxaVigente)
                .orElseThrow(() -> new ReferenciaNaoEncontradaException(
                        "Taxa base nao cadastrada para a moeda " + moeda));
    }
}
