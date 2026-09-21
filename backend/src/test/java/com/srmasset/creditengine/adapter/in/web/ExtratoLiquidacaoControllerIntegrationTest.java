package com.srmasset.creditengine.adapter.in.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Ver SPEC.md item 12: o endpoint le via SQL nativo (JdbcTemplate), fora da
 * sessao do Hibernate - por isso os testes chamam {@link EntityManager#flush()}
 * explicitamente apos liquidar via MockMvc e antes de consultar o extrato, ja
 * que a classe de teste inteira roda numa unica transacao nunca comitada
 * (@Transactional de teste) e sem isso a atualizacao ficaria pendente na
 * sessao do Hibernate, invisivel para a query nativa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class ExtratoLiquidacaoControllerIntegrationTest {

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
    @PersistenceContext
    private EntityManager entityManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private void criarPrecificarELiquidar(String ativo, String moedaPagamento) throws Exception {
        // RecebivelRequest.moedaPagamento e' opcional - omitido (null) para BRL, explicito para
        // cross-currency (ver javadoc de RecebivelRequest: nao ha campo "moeda" no payload).
        String campoMoeda = "BRL".equals(moedaPagamento) ? "" : ("\"moedaPagamento\": \"" + moedaPagamento + "\",");
        String payload = """
                {
                  "recebiveis": [
                    {
                      "ativo": "%s",
                      "valorBruto": "1000.00",
                      %s
                      "dataVencimento": "%s",
                      "categoriaRisco": "B"
                    }
                  ]
                }
                """.formatted(ativo, campoMoeda, LocalDate.now().plusDays(60));

        String body = mockMvc.perform(post("/api/v1/lotes-recebiveis")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode lote = objectMapper.readTree(body);
        String loteId = lote.get("id").asText();
        String recebivelId = lote.get("recebiveis").get(0).get("id").asText();

        mockMvc.perform(put("/api/v1/lotes-recebiveis/{loteId}/recebiveis/{recebivelId}/liquidacao",
                        loteId, recebivelId))
                .andExpect(status().isOk());
    }

    @Test
    void listaExtratoComRecebiveisLiquidadosEIgnoraOsNaoLiquidados() throws Exception {
        criarPrecificarELiquidar("Duplicata Extrato A", "BRL");

        // criado e precificado, mas nunca liquidado - nao deve aparecer no extrato
        String payloadPendente = """
                {
                  "recebiveis": [
                    {
                      "ativo": "Nao liquidado",
                      "valorBruto": "500.00",
                      "moeda": "BRL",
                      "dataVencimento": "%s",
                      "categoriaRisco": "B"
                    }
                  ]
                }
                """.formatted(LocalDate.now().plusDays(60));
        mockMvc.perform(post("/api/v1/lotes-recebiveis")
                        .contentType("application/json")
                        .content(payloadPendente))
                .andExpect(status().isCreated());

        entityManager.flush();

        mockMvc.perform(get("/api/v1/extrato-liquidacao").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].ativo").value("Duplicata Extrato A"))
                .andExpect(jsonPath("$.content[0].liquidadoEm").exists())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20));
    }

    @Test
    void filtraExtratoPorAtivoParcialEPorMoeda() throws Exception {
        criarPrecificarELiquidar("Duplicata Mercantil Extrato", "BRL");
        criarPrecificarELiquidar("Cheque Extrato", "USD");
        entityManager.flush();

        mockMvc.perform(get("/api/v1/extrato-liquidacao").param("ativo", "duplicata"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].ativo").value("Duplicata Mercantil Extrato"));

        mockMvc.perform(get("/api/v1/extrato-liquidacao").param("moeda", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].ativo").value("Cheque Extrato"));
    }

    @Test
    void retorna400QuandoMoedaInvalida() throws Exception {
        mockMvc.perform(get("/api/v1/extrato-liquidacao").param("moeda", "EUR"))
                .andExpect(status().isBadRequest());
    }
}
