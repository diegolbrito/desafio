package com.srmasset.creditengine.adapter.in.web.dto;

import com.srmasset.creditengine.domain.CategoriaRisco;
import com.srmasset.creditengine.domain.Moeda;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Data de vencimento nao tem validacao de "deve ser futura" aqui de proposito:
 * essa regra e' de negocio (rejeita so o item, sem abortar o lote - ver SPEC.md
 * "Premissas adotadas" item 5) e e' aplicada no dominio (Recebivel.calcularPrazoMeses),
 * nao na validacao estrutural do payload.
 *
 * <p>Campos BigDecimal levam {@code @Schema(type = "string")} porque o swagger-core
 * trata BigDecimal como tipo primitivo "number" por padrao, mas a API de fato
 * serializa dinheiro/taxas como string (ver JacksonConfig e SPEC.md, "Tipos de
 * dados canonicos") - sem a anotacao, o schema OpenAPI ficaria errado e a geracao
 * de tipos do frontend (Etapa 6) produziria "number" em vez de "string".
 */
public record RecebivelRequest(
        @NotBlank(message = "Cedente e obrigatorio") String cedente,
        @NotNull(message = "Valor bruto e obrigatorio") @Positive(message = "Valor bruto deve ser positivo")
        @Schema(type = "string", example = "15000.00") BigDecimal valorBruto,
        @NotNull(message = "Moeda e obrigatoria") Moeda moeda,
        @NotNull(message = "Data de vencimento e obrigatoria") LocalDate dataVencimento,
        @NotNull(message = "Categoria de risco e obrigatoria") CategoriaRisco categoriaRisco) {
}
