package com.srmasset.creditengine.adapter.out.persistence;

import com.srmasset.creditengine.adapter.out.persistence.entity.RecebivelEntity;
import com.srmasset.creditengine.adapter.out.persistence.repository.RecebivelJpaRepository;
import com.srmasset.creditengine.application.port.out.LiquidarRecebivelPort;
import com.srmasset.creditengine.domain.Recebivel;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class RecebivelLiquidacaoPersistenceAdapter implements LiquidarRecebivelPort {

    private final RecebivelJpaRepository repository;

    public RecebivelLiquidacaoPersistenceAdapter(RecebivelJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<Recebivel> buscarParaLiquidar(UUID loteId, UUID recebivelId) {
        return repository.buscarParaLiquidar(loteId, recebivelId).map(this::paraDominio);
    }

    @Override
    public void salvar(Recebivel recebivel) {
        RecebivelEntity entity = repository.findById(recebivel.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Recebivel nao encontrado ao salvar liquidacao: " + recebivel.getId()));
        entity.aplicarLiquidacao(recebivel.getStatus(), recebivel.getLiquidadoEm());
        repository.save(entity);
    }

    private Recebivel paraDominio(RecebivelEntity entity) {
        return Recebivel.reconstituir(entity.getId(), entity.getAtivo(), entity.getValorBruto(),
                entity.getDataVencimento(), entity.getCategoriaRisco(), entity.getMoedaPagamento(),
                entity.getStatus(), entity.getValorPresente(), entity.getValorDesagio(),
                entity.getTaxaDescontoAplicada(), entity.getCotacaoCambio(), entity.getMotivoRejeicao(),
                entity.getLiquidadoEm());
    }
}
