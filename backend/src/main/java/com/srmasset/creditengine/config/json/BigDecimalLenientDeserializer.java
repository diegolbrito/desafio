package com.srmasset.creditengine.config.json;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.math.BigDecimal;

/** Aceita o valor tanto como string (formato oficial da API) quanto como number, por tolerancia. */
public class BigDecimalLenientDeserializer extends ValueDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser parser, DeserializationContext context) throws JacksonException {
        String texto = parser.getValueAsString();
        if (texto == null || texto.isBlank()) {
            return null;
        }
        return new BigDecimal(texto);
    }
}
