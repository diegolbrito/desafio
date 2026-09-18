package com.srmasset.creditengine.adapter.out.persistence.repository;

import com.srmasset.creditengine.adapter.out.persistence.entity.TransacaoEventoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TransacaoEventoJpaRepository extends JpaRepository<TransacaoEventoEntity, UUID> {
}
