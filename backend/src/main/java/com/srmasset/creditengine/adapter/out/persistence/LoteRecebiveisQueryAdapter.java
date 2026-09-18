package com.srmasset.creditengine.adapter.out.persistence;

import com.srmasset.creditengine.adapter.out.persistence.entity.LoteRecebivelEntity;
import com.srmasset.creditengine.adapter.out.persistence.entity.RecebivelEntity;
import com.srmasset.creditengine.adapter.out.persistence.repository.LoteRecebivelJpaRepository;
import com.srmasset.creditengine.application.port.out.BuscarLoteRecebiveisPort;
import com.srmasset.creditengine.application.port.out.DirecaoOrdenacao;
import com.srmasset.creditengine.application.port.out.ListarLotesRecebiveisPort;
import com.srmasset.creditengine.application.port.out.LoteRecebiveisDetalhe;
import com.srmasset.creditengine.application.port.out.LoteRecebiveisResumo;
import com.srmasset.creditengine.application.port.out.PaginaResultado;
import com.srmasset.creditengine.application.port.out.RecebivelLeitura;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Lado de consulta (somente leitura), separado do adapter de escrita
 * (LoteRecebiveisPersistenceAdapter) para manter cada classe com uma unica
 * responsabilidade. A listagem usa uma projecao leve (sem recebiveis) para
 * evitar N+1 numa pagina com varios lotes; o detalhe usa JOIN FETCH porque e'
 * uma unica linha.
 */
@Component
public class LoteRecebiveisQueryAdapter implements ListarLotesRecebiveisPort, BuscarLoteRecebiveisPort {

    private final LoteRecebivelJpaRepository repository;

    public LoteRecebiveisQueryAdapter(LoteRecebivelJpaRepository repository) {
        this.repository = repository;
    }

    @Override
    public PaginaResultado<LoteRecebiveisResumo> listar(int page, int size, String campoOrdenacao, DirecaoOrdenacao direcao) {
        Sort.Direction direction = direcao == DirecaoOrdenacao.ASC ? Sort.Direction.ASC : Sort.Direction.DESC;
        Page<LoteRecebivelEntity> pagina = repository.findAll(PageRequest.of(page, size, Sort.by(direction, campoOrdenacao)));

        List<LoteRecebiveisResumo> content = pagina.getContent().stream()
                .map(this::paraResumo)
                .toList();

        return new PaginaResultado<>(content, pagina.getNumber(), pagina.getSize(),
                pagina.getTotalElements(), pagina.getTotalPages());
    }

    @Override
    public Optional<LoteRecebiveisDetalhe> buscarPorId(UUID id) {
        return repository.buscarComRecebiveisPorId(id).map(this::paraDetalhe);
    }

    private LoteRecebiveisResumo paraResumo(LoteRecebivelEntity entity) {
        return new LoteRecebiveisResumo(entity.getId(), entity.getDataReferencia(), entity.getStatus(), entity.getCreatedAt());
    }

    private LoteRecebiveisDetalhe paraDetalhe(LoteRecebivelEntity entity) {
        List<RecebivelLeitura> recebiveis = entity.getRecebiveis().stream()
                .map(this::paraRecebivelLeitura)
                .toList();
        return new LoteRecebiveisDetalhe(entity.getId(), entity.getDataReferencia(), entity.getStatus(),
                entity.getCreatedAt(), recebiveis);
    }

    private RecebivelLeitura paraRecebivelLeitura(RecebivelEntity entity) {
        return new RecebivelLeitura(entity.getId(), entity.getCedente(), entity.getValorBruto(), entity.getMoeda(),
                entity.getDataVencimento(), entity.getCategoriaRisco(), entity.getStatus(), entity.getValorPresente(),
                entity.getValorDesagio(), entity.getTaxaDescontoAplicada(), entity.getMotivoRejeicao());
    }
}
