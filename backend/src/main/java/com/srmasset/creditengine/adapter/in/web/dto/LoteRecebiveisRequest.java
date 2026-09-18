package com.srmasset.creditengine.adapter.in.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record LoteRecebiveisRequest(
        @NotEmpty(message = "Lote deve conter ao menos um recebivel") List<@Valid RecebivelRequest> recebiveis) {
}
