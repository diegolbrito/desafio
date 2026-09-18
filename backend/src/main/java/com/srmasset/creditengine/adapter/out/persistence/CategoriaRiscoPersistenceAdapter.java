package com.srmasset.creditengine.adapter.out.persistence;

import com.srmasset.creditengine.adapter.out.persistence.entity.CategoriaRiscoEntity;
import com.srmasset.creditengine.adapter.out.persistence.repository.CategoriaRiscoJpaRepository;
import com.srmasset.creditengine.application.exception.ReferenciaNaoEncontradaException;
import com.srmasset.creditengine.application.port.out.CategoriaRiscoRepositoryPort;
import com.srmasset.creditengine.domain.CategoriaRisco;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class CategoriaRiscoPersistenceAdapter implements CategoriaRiscoRepositoryPort {

    private final CategoriaRiscoJpaRepository repository;

    public CategoriaRiscoPersistenceAdapter(CategoriaRiscoJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public BigDecimal buscarSpread(CategoriaRisco categoriaRisco) {
        return repository.findByCodigo(categoriaRisco)
                .map(CategoriaRiscoEntity::getSpreadRisco)
                .orElseThrow(() -> new ReferenciaNaoEncontradaException(
                        "Spread de risco nao cadastrado para a categoria " + categoriaRisco));
    }
}
