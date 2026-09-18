package com.srmasset.creditengine.config.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.math.BigDecimal;

/**
 * Dinheiro e taxas trafegam como string no JSON, nunca como number, para
 * preservar precisao (ver SPEC.md, "Tipos de dados canonicos").
 * toPlainString() evita notacao cientifica para valores muito pequenos.
 */
public class BigDecimalPlainStringSerializer extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator gen, SerializationContext context) throws JacksonException {
        gen.writeString(value.toPlainString());
    }
}
