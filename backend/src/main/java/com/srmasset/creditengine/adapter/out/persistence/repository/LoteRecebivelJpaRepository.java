package com.srmasset.creditengine.adapter.out.persistence.repository;

import com.srmasset.creditengine.adapter.out.persistence.entity.LoteRecebivelEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoteRecebivelJpaRepository extends JpaRepository<LoteRecebivelEntity, UUID> {
}
