package com.srmasset.creditengine.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS e' uma politica de navegador (quais origens podem chamar a API),
 * independente de autenticacao - o SPEC.md diz para nao usar protecao de auth
 * nas APIs, mas sem isso o frontend (porta diferente da API) seria bloqueado
 * pelo navegador. Origem permitida configuravel via ambiente, igual a
 * VITE_API_BASE_URL do frontend.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${credit-engine.cors.allowed-origins}")
    private String[] allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Location", "X-Correlation-Id");
    }
}
