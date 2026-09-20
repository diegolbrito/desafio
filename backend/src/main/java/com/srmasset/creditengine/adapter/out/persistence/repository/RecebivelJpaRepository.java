package com.srmasset.creditengine.adapter.out.persistence.repository;

import com.srmasset.creditengine.adapter.out.persistence.entity.RecebivelEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RecebivelJpaRepository extends JpaRepository<RecebivelEntity, UUID> {

    /**
     * SELECT ... FOR UPDATE: serializa chamadas concorrentes de liquidacao do
     * mesmo recebivel (ver LiquidarRecebivelPort e SPEC.md "Premissas adotadas" -
     * liquidacao). Sem isso, duas requisicoes simultaneas poderiam ambas ler
     * status=PRECIFICADO antes de qualquer uma commitar, e ambas liquidarem.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM RecebivelEntity r WHERE r.id = :recebivelId AND r.loteRecebivel.id = :loteId")
    Optional<RecebivelEntity> buscarParaLiquidar(@Param("loteId") UUID loteId, @Param("recebivelId") UUID recebivelId);
}
