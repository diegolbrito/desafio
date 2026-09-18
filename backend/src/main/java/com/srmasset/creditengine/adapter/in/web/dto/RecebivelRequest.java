package com.srmasset.creditengine.adapter.in.web.dto;

import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.Moeda;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data de vencimento nao tem validacao de "deve ser futura" aqui de proposito:
 * essa regra e' de negocio (rejeita so o item, sem abortar o lote - ver SPEC.md
 * "Premissas adotadas" item 5) e e' aplicada no dominio (Recebivel.calcularPrazoDias),
 * nao na validacao estrutural do payload.
 */
public record RecebivelRequest(
        @NotBlank(message = "Cedente e obrigatorio") String cedente,
        @NotNull(message = "Valor bruto e obrigatorio") @Positive(message = "Valor bruto deve ser positivo") BigDecimal valorBruto,
        @NotNull(message = "Moeda e obrigatoria") Moeda moeda,
        @NotNull(message = "Data de vencimento e obrigatoria") LocalDate dataVencimento,
        @NotNull(message = "Categoria de risco e obrigatoria") CategoriaRisco categoriaRisco) {
}
