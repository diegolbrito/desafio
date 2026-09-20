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
 *
 * <p>O ativo e' sempre denominado em BRL (ver SPEC.md, "Premissas adotadas" item 3) - por isso
 * nao ha campo {@code moeda} aqui, so' {@code moedaPagamento}. {@code moedaPagamento} e'
 * opcional: quando omitido (ou igual a BRL), nao ha conversao cambial. Quando diferente de BRL
 * (cross-currency), a cotacao usada na conversao NAO e' recebida aqui - e' buscada pelo backend
 * num servico HTTP externo (ver SPEC.md "Premissas adotadas" item 11; com fallback para a ultima
 * cotacao conhecida ou um valor estatico de seguranca se esse servico estiver fora do ar).
 */
public record RecebivelRequest(
        @NotBlank(message = "Ativo e obrigatorio") String ativo,
        @NotNull(message = "Valor bruto e obrigatorio") @Positive(message = "Valor bruto deve ser positivo")
        @Schema(type = "string", example = "15000.00") BigDecimal valorBruto,
        @NotNull(message = "Data de vencimento e obrigatoria") LocalDate dataVencimento,
        @NotNull(message = "Categoria de risco e obrigatoria") CategoriaRisco categoriaRisco,
        @Schema(description = "Moeda em que o recebivel e' efetivamente pago, se diferente de BRL "
                + "(cross-currency). Omitir quando o pagamento e' em BRL (mesma moeda do ativo).")
        Moeda moedaPagamento) {
}
