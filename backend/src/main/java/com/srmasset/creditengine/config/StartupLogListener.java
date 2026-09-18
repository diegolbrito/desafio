package com.srmasset.creditengine.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Loga perfil ativo e porta assim que a aplicacao termina de subir (ver SPEC.md > "Logging"). */
@Component
public class StartupLogListener implements ApplicationListener<ApplicationReadyEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupLogListener.class);

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        Environment env = event.getApplicationContext().getEnvironment();
        String[] perfis = env.getActiveProfiles();
        String perfilAtivo = perfis.length == 0 ? "default" : String.join(",", perfis);
        String porta = env.getProperty("server.port", "8080");
        log.info("Aplicacao pronta: perfil(is)={}, porta={}", perfilAtivo, porta);
    }
}
