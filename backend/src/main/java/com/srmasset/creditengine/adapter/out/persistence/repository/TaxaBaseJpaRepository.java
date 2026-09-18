package com.srmasset.creditengine.adapter.out.persistence.repository;

import com.srmasset.creditengine.adapter.out.persistence.entity.TaxaBaseEntity;
import com.srmasset.creditengine.domain.Moeda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaxaBaseJpaRepository extends JpaRepository<TaxaBaseEntity, UUID> {

    Optional<TaxaBaseEntity> findByMoeda(Moeda moeda);
}
