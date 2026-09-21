package com.srmasset.creditengine.adapter.in.web;

import com.srmasset.creditengine.adapter.in.web.dto.ExtratoLiquidacaoItemResponse;
import com.srmasset.creditengine.adapter.in.web.dto.PaginaResponse;
import com.srmasset.creditengine.application.port.in.BuscarExtratoLiquidacaoUseCase;
import com.srmasset.creditengine.application.port.out.FiltroExtratoLiquidacao;
import com.srmasset.creditengine.domain.Moeda;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/**
 * Relatorio cross-lote (nao aninhado em lotes-recebiveis de proposito - e' uma
 * visao analitica, nao o detalhe de um lote especifico). Ver SPEC.md item 12
 * sobre a escolha de SQL nativo para esta consulta.
 */
@RestController
@RequestMapping("/api/v1/extrato-liquidacao")
@Tag(name = "Extrato de liquidacao", description = "Relatorio analitico de recebiveis liquidados, com filtro por periodo/ativo/moeda")
public class ExtratoLiquidacaoController {

    private final BuscarExtratoLiquidacaoUseCase buscarExtratoLiquidacaoUseCase;

    public ExtratoLiquidacaoController(BuscarExtratoLiquidacaoUseCase buscarExtratoLiquidacaoUseCase) {
        this.buscarExtratoLiquidacaoUseCase = buscarExtratoLiquidacaoUseCase;
    }

    @GetMapping
    @Operation(summary = "Lista recebiveis liquidados, paginado",
            description = "Filtros opcionais: periodo (dataInicio/dataFim sobre liquidadoEm), ativo "
                    + "(busca parcial, case-insensitive) e moeda (moedaPagamento). So' recebiveis com "
                    + "status LIQUIDADO aparecem aqui.")
    public PaginaResponse<ExtratoLiquidacaoItemResponse> buscar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime dataFim,
            @RequestParam(required = false) String ativo,
            @RequestParam(required = false) Moeda moeda,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        FiltroExtratoLiquidacao filtro = new FiltroExtratoLiquidacao(dataInicio, dataFim, ativo, moeda);
        var pagina = buscarExtratoLiquidacaoUseCase.buscar(filtro, page, size);
        return PaginaResponse.from(pagina, ExtratoLiquidacaoItemResponse::from);
    }
}
