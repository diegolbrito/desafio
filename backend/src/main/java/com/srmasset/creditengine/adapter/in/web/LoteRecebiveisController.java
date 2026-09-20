package com.srmasset.creditengine.adapter.in.web;

import com.srmasset.creditengine.adapter.in.web.dto.LoteRecebiveisRequest;
import com.srmasset.creditengine.adapter.in.web.dto.LoteRecebiveisResponse;
import com.srmasset.creditengine.adapter.in.web.dto.LoteRecebiveisResumoResponse;
import com.srmasset.creditengine.adapter.in.web.dto.PaginaResponse;
import com.srmasset.creditengine.adapter.in.web.dto.RecebivelRequest;
import com.srmasset.creditengine.adapter.in.web.exception.RecursoNaoEncontradoException;
import com.srmasset.creditengine.application.port.in.BuscarLoteRecebiveisUseCase;
import com.srmasset.creditengine.application.port.in.ComandoPrecificarLote;
import com.srmasset.creditengine.application.port.in.ListarLotesRecebiveisUseCase;
import com.srmasset.creditengine.application.port.in.PrecificarLoteUseCase;
import com.srmasset.creditengine.application.port.out.DirecaoOrdenacao;
import com.srmasset.creditengine.domain.LoteRecebiveis;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * Limite transacional do fluxo de precificacao: fica aqui (adapter), nao no
 * PrecificarLoteService, para manter a camada de aplicacao livre de anotacoes
 * do Spring (arquitetura hexagonal "rigorosa" pedida no SPEC). Como este
 * metodo chama o caso de uso, os varios save() feitos por ele (lote +
 * eventos de auditoria) ficam na mesma transacao.
 */
@RestController
@RequestMapping("/api/v1/lotes-recebiveis")
@Tag(name = "Lotes de recebiveis", description = "Recebimento, precificacao e consulta de lotes de recebiveis")
public class LoteRecebiveisController {

    private final PrecificarLoteUseCase precificarLoteUseCase;
    private final ListarLotesRecebiveisUseCase listarLotesUseCase;
    private final BuscarLoteRecebiveisUseCase buscarLoteUseCase;

    public LoteRecebiveisController(PrecificarLoteUseCase precificarLoteUseCase,
                                     ListarLotesRecebiveisUseCase listarLotesUseCase,
                                     BuscarLoteRecebiveisUseCase buscarLoteUseCase) {
        this.precificarLoteUseCase = precificarLoteUseCase;
        this.listarLotesUseCase = listarLotesUseCase;
        this.buscarLoteUseCase = buscarLoteUseCase;
    }

    @PostMapping
    @Transactional
    @Operation(summary = "Recebe e precifica um lote de recebiveis",
            description = "Calcula o desagio de cada recebivel do lote e registra o resultado de forma auditavel. "
                    + "Retorna 201 mesmo se algum recebivel for rejeitado individualmente (ver campo status de cada item).")
    @ApiResponse(responseCode = "201", description = "Lote recebido e processado")
    public ResponseEntity<LoteRecebiveisResponse> criar(@Valid @RequestBody LoteRecebiveisRequest request,
                                                          UriComponentsBuilder uriBuilder) {
        ComandoPrecificarLote comando = paraComando(request);
        LoteRecebiveis lote = precificarLoteUseCase.precificar(comando);

        URI location = uriBuilder.path("/api/v1/lotes-recebiveis/{id}").buildAndExpand(lote.getId()).toUri();
        return ResponseEntity.created(location).body(LoteRecebiveisResponse.from(lote));
    }

    @GetMapping
    @Operation(summary = "Lista os lotes de recebiveis, paginado",
            description = "Ordenacao por createdAt, dataReferencia ou status (formato: campo,asc|desc).")
    public PaginaResponse<LoteRecebiveisResumoResponse> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort) {
        String[] partes = sort.split(",", 2);
        String campoOrdenacao = partes[0];
        DirecaoOrdenacao direcao = partes.length > 1 && "asc".equalsIgnoreCase(partes[1])
                ? DirecaoOrdenacao.ASC
                : DirecaoOrdenacao.DESC;

        var pagina = listarLotesUseCase.listar(page, size, campoOrdenacao, direcao);
        return PaginaResponse.from(pagina, LoteRecebiveisResumoResponse::from);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca o detalhe de um lote, com seus recebiveis precificados")
    public LoteRecebiveisResponse buscarPorId(@PathVariable UUID id) {
        return buscarLoteUseCase.buscarPorId(id)
                .map(LoteRecebiveisResponse::from)
                .orElseThrow(() -> new RecursoNaoEncontradoException(
                        "Lote de recebiveis nao encontrado: " + id));
    }

    private ComandoPrecificarLote paraComando(LoteRecebiveisRequest request) {
        return new ComandoPrecificarLote(request.recebiveis().stream()
                .map(this::paraComandoRecebivel)
                .toList());
    }

    private ComandoPrecificarLote.ComandoRecebivel paraComandoRecebivel(RecebivelRequest request) {
        return new ComandoPrecificarLote.ComandoRecebivel(request.ativo(), request.valorBruto(),
                request.dataVencimento(), request.categoriaRisco(), request.moedaPagamento());
    }
}
