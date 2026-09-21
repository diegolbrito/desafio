package com.srmasset.creditengine.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de contrato: o `openapi.yaml` commitado na raiz do repo (referenciado no
 * README, usado por consumidores externos da API) precisa refletir exatamente o
 * que a aplicacao realmente serve em `/v3/api-docs.yaml` (gerado em runtime pelo
 * springdoc a partir das anotacoes do codigo). Sem este teste, o arquivo pode
 * divergir silenciosamente do comportamento real da API sempre que um endpoint
 * for adicionado/alterado sem regenerar o arquivo manualmente (foi exatamente o
 * que ja tinha acontecido: o arquivo nao tinha o endpoint de extrato de
 * liquidacao ate esta correcao).
 *
 * <p>Compara as duas specs como estrutura (Map, via SnakeYAML - ja e' dependencia
 * transitiva do Spring Boot, nenhuma lib nova), nao como texto bruto, para nao
 * quebrar por diferenca irrelevante de formatacao/ordem de chaves.
 *
 * <p>Se este teste falhar: regenerar com
 * {@code curl http://localhost:8080/v3/api-docs.yaml -o openapi.yaml} (backend
 * rodando via docker-compose) e commitar o arquivo atualizado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class OpenApiContractTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:18");

    @DynamicPropertySource
    static void configurarDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    void openApiYamlCommitadoRefleteOQueAAplicacaoRealmenteServe() throws Exception {
        String specGerada = mockMvc.perform(get("/v3/api-docs.yaml"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Yaml yaml = new Yaml();
        Map<String, Object> specGeradaMap = yaml.load(specGerada);
        Map<String, Object> specCommitadaMap = yaml.load(Files.readString(caminhoOpenApiYamlNaRaizDoRepo()));

        // "servers" e' inferido pelo springdoc a partir da requisicao HTTP usada para gerar a spec
        // (host/porta reais) - MockMvc simula uma requisicao sem porta real, entao esse campo
        // sempre diverge do que "curl http://localhost:8080/..." produz. Nao faz parte do contrato
        // (endpoints/schemas/parametros), por isso e' ignorado na comparacao.
        specGeradaMap.remove("servers");
        specCommitadaMap.remove("servers");

        assertThat(specGeradaMap)
                .as("openapi.yaml na raiz do repo esta desatualizado em relacao a API real - "
                        + "regenere com: curl http://localhost:8080/v3/api-docs.yaml -o openapi.yaml")
                .isEqualTo(specCommitadaMap);
    }

    private static Path caminhoOpenApiYamlNaRaizDoRepo() throws IOException {
        // O working directory do teste e' o modulo backend/ (Maven); o arquivo commitado
        // vive na raiz do repo, um nivel acima.
        Path caminho = Path.of(System.getProperty("user.dir"), "..", "openapi.yaml").normalize();
        if (!Files.exists(caminho)) {
            throw new IOException("openapi.yaml nao encontrado em " + caminho);
        }
        return caminho;
    }
}
