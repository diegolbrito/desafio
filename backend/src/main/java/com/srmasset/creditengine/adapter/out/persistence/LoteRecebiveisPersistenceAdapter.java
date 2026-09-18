package com.srmasset.creditengine.adapter.out.persistence;

import com.srmasset.creditengine.adapter.out.persistence.entity.LoteRecebivelEntity;
import com.srmasset.creditengine.adapter.out.persistence.entity.RecebivelEntity;
import com.srmasset.creditengine.adapter.out.persistence.repository.LoteRecebivelJpaRepository;
import com.srmasset.creditengine.application.port.out.SalvarLoteRecebiveisPort;
import com.srmasset.creditengine.domain.LoteRecebiveis;
import com.srmasset.creditengine.domain.Recebivel;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Mapeia o agregado LoteRecebiveis (+ Recebivel) para/de entidades JPA. Como o
 * lote acabou de ser processado em memoria pelo caso de uso, a entidade salva
 * e' a mesma lista/instancia (sem reconsulta), entao a ordem dos recebiveis
 * persistidos e' garantida igual a' ordem do agregado de dominio.
 */
@Component
public class LoteRecebiveisPersistenceAdapter implements SalvarLoteRecebiveisPort {

    private final LoteRecebivelJpaRepository repository;

    public LoteRecebiveisPersistenceAdapter(LoteRecebivelJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public LoteRecebiveis salvar(LoteRecebiveis lote) {
        LoteRecebivelEntity entity = new LoteRecebivelEntity(lote.getDataReferencia(), lote.getStatus());
        for (Recebivel recebivel : lote.getRecebiveis()) {
            entity.adicionarRecebivel(paraEntity(recebivel));
        }

        LoteRecebivelEntity salvo = repository.save(entity);

        lote.atribuirId(salvo.getId());
        List<Recebivel> recebiveisDominio = lote.getRecebiveis();
        List<RecebivelEntity> recebiveisEntidade = salvo.getRecebiveis();
        for (int i = 0; i < recebiveisDominio.size(); i++) {
            recebiveisDominio.get(i).atribuirId(recebiveisEntidade.get(i).getId());
        }

        return lote;
    }

    private RecebivelEntity paraEntity(Recebivel recebivel) {
        return new RecebivelEntity(
                recebivel.getCedente(), recebivel.getValorBruto(), recebivel.getMoeda(),
                recebivel.getDataVencimento(), recebivel.getCategoriaRisco(), recebivel.getStatus(),
                recebivel.getValorPresente(), recebivel.getValorDesagio(),
                recebivel.getTaxaDescontoAplicada(), recebivel.getMotivoRejeicao());
    }
}
