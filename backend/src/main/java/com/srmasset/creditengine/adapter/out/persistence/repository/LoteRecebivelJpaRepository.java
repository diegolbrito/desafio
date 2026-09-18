package com.srmasset.creditengine.adapter.out.persistence.repository;

import com.srmasset.creditengine.adapter.out.persistence.entity.LoteRecebivelEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface LoteRecebivelJpaRepository extends JpaRepository<LoteRecebivelEntity, UUID> {

    /**
     * JOIN FETCH para trazer os recebiveis numa unica consulta (busca de um unico
     * lote - sem os problemas de paginacao+fetch-join que existem em listagens).
     */
    @Query("SELECT DISTINCT l FROM LoteRecebivelEntity l LEFT JOIN FETCH l.recebiveis WHERE l.id = :id")
    Optional<LoteRecebivelEntity> buscarComRecebiveisPorId(@Param("id") UUID id);
}
