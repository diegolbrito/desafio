package com.srmasset.creditengine.adapter.out.persistence.repository;

import com.srmasset.creditengine.adapter.out.persistence.entity.CategoriaRiscoEntity;
import com.srmasset.creditengine.domain.CategoriaRisco;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CategoriaRiscoJpaRepository extends JpaRepository<CategoriaRiscoEntity, UUID> {

    Optional<CategoriaRiscoEntity> findByCodigo(CategoriaRisco codigo);
}
