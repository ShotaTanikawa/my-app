package com.example.backend.supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * 主要ユースケースの回帰を守る統合テスト。
 */

@SpringBootTest
@AutoConfigureMockMvc
class SupplierContractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanCreateSupplierAndContractAndSeeSuggestionWithContractRules() throws Exception {
        String adminToken = login("admin", "admin123");
        long supplierId = createSupplier(adminToken, "S-" + System.currentTimeMillis());
        long productId = createProduct(adminToken, "SUP-CONTRACT-" + System.currentTimeMillis(), 10, 7);

        mockMvc.perform(
                        post("/api/products/{productId}/suppliers", productId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "supplierId", supplierId,
                                        "unitCost", 550,
                                        "leadTimeDays", 5,
                                        "moq", 12,
                                        "lotSize", 10,
                                        "primary", true
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.supplierId").value(supplierId))
                .andExpect(jsonPath("$.primary").value(true));

        MvcResult suggestionsResult = mockMvc.perform(
                        get("/api/purchase-orders/suggestions")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode suggestions = objectMapper.readTree(suggestionsResult.getResponse().getContentAsString());
        JsonNode matched = null;
        for (JsonNode suggestion : suggestions) {
            if (suggestion.path("productId").asLong() == productId) {
                matched = suggestion;
                break;
            }
        }

        assertTrue(matched != null, "suggestion for product should exist");
        assertTrue(matched.path("suggestedSupplierId").asLong() == supplierId);
        assertTrue(matched.path("moq").asInt() == 12);
        assertTrue(matched.path("lotSize").asInt() == 10);
        // available=0, reorderPoint=10, reorderQuantity=7 => base=17, moq=12, lotSize=10 => 20
        assertTrue(matched.path("suggestedQuantity").asInt() == 20);
    }

    @Test
    void viewerCannotCreateSupplierOrContract() throws Exception {
        String adminToken = login("admin", "admin123");
        String viewerToken = login("viewer", "viewer123");
        long supplierId = createSupplier(adminToken, "S-VIEW-" + System.currentTimeMillis());
        long productId = createProduct(adminToken, "SUP-VIEW-" + System.currentTimeMillis(), 10, 5);

        mockMvc.perform(
                        post("/api/suppliers")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "code", "VIEW-BLOCK",
                                        "name", "Viewer Blocked Supplier"
                                )))
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/products/{productId}/suppliers", productId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "supplierId", supplierId,
                                        "unitCost", 650,
                                        "leadTimeDays", 2,
                                        "moq", 5,
                                        "lotSize", 5,
                                        "primary", true
                                )))
                )
                .andExpect(status().isForbidden());
    }

    private long createSupplier(String token, String code) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/suppliers")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "code", code,
                                        "name", "Supplier " + code
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private long createProduct(String token, String sku, int reorderPoint, int reorderQuantity) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", sku,
                                        "name", "Supplier Test Product",
                                        "unitPrice", 1200,
                                        "reorderPoint", reorderPoint,
                                        "reorderQuantity", reorderQuantity
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "username", username,
                                        "password", password
                                )))
                )
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("accessToken").asText();
    }
}
