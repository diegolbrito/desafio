package com.srmasset.creditengine.config;

import com.srmasset.creditengine.config.json.BigDecimalLenientDeserializer;
import com.srmasset.creditengine.config.json.BigDecimalPlainStringSerializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

import java.math.BigDecimal;

@Configuration
public class JacksonConfig {

    @Bean
    public JacksonModule bigDecimalAsStringModule() {
        SimpleModule module = new SimpleModule("BigDecimalAsString");
        module.addSerializer(BigDecimal.class, new BigDecimalPlainStringSerializer());
        module.addDeserializer(BigDecimal.class, new BigDecimalLenientDeserializer());
        return module;
    }
}
