package com.srmasset.creditengine.adapter.in.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class LoteRecebiveisControllerIntegrationTest {

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
    void criaEPrecificaLoteComSucesso() throws Exception {
        String payload = """
                {
                  "recebiveis": [
                    {
                      "cedente": "Cedente A",
                      "valorBruto": "1000.00",
                      "moeda": "BRL",
                      "dataVencimento": "%s",
                      "categoriaRisco": "B"
                    }
                  ]
                }
                """.formatted(LocalDate.now().plusDays(60));

        mockMvc.perform(post("/api/v1/lotes-recebiveis")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.status").value("PRECIFICADO"))
                .andExpect(jsonPath("$.recebiveis", hasSize(1)))
                .andExpect(jsonPath("$.recebiveis[0].status").value("PRECIFICADO"))
                // valores monetarios/taxas devem trafegar como STRING no JSON, nunca number
                .andExpect(jsonPath("$.recebiveis[0].valorBruto").value(matchesPattern("\\d+\\.\\d+")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"valorBruto\":\"1000.00\"")));
    }

    @Test
    void rejeitaApenasItemComVencimentoInvalidoMasCriaOLote() throws Exception {
        String payload = """
                {
                  "recebiveis": [
                    {
                      "cedente": "Cedente Invalido",
                      "valorBruto": "500.00",
                      "moeda": "BRL",
                      "dataVencimento": "%s",
                      "categoriaRisco": "A"
                    },
                    {
                      "cedente": "Cedente Valido",
                      "valorBruto": "500.00",
                      "moeda": "BRL",
                      "dataVencimento": "%s",
                      "categoriaRisco": "A"
                    }
                  ]
                }
                """.formatted(LocalDate.now(), LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/v1/lotes-recebiveis")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PRECIFICADO"))
                .andExpect(jsonPath("$.recebiveis[0].status").value("REJEITADO"))
                .andExpect(jsonPath("$.recebiveis[0].motivoRejeicao").exists())
                .andExpect(jsonPath("$.recebiveis[1].status").value("PRECIFICADO"));
    }

    @Test
    void retorna400QuandoLoteNaoTemRecebiveis() throws Exception {
        mockMvc.perform(post("/api/v1/lotes-recebiveis")
                        .contentType("application/json")
                        .content("{\"recebiveis\": []}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Erro de validacao"))
                .andExpect(jsonPath("$.errors").exists());
    }

    @Test
    void retorna400QuandoValorBrutoNaoEhPositivo() throws Exception {
        String payload = """
                {
                  "recebiveis": [
                    {
                      "cedente": "Cedente A",
                      "valorBruto": "-10.00",
                      "moeda": "BRL",
                      "dataVencimento": "%s",
                      "categoriaRisco": "A"
                    }
                  ]
                }
                """.formatted(LocalDate.now().plusDays(30));

        mockMvc.perform(post("/api/v1/lotes-recebiveis")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isBadRequest());
    }

    @Test
    void buscaLotePorIdRetorna404QuandoNaoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/lotes-recebiveis/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Recurso nao encontrado"));
    }

    @Test
    void criaBuscaEListaLote() throws Exception {
        String payload = """
                {
                  "recebiveis": [
                    {
                      "cedente": "Cedente Consulta",
                      "valorBruto": "2000.00",
                      "moeda": "USD",
                      "dataVencimento": "%s",
                      "categoriaRisco": "C"
                    }
                  ]
                }
                """.formatted(LocalDate.now().plusDays(90));

        String location = mockMvc.perform(post("/api/v1/lotes-recebiveis")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recebiveis[0].cedente").value("Cedente Consulta"));

        mockMvc.perform(get("/api/v1/lotes-recebiveis").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }
}
