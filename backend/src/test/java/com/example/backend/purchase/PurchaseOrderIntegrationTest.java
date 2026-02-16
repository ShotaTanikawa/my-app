package com.example.backend.purchase;

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

import java.util.List;
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
class PurchaseOrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void operatorCanCreateAndReceivePurchaseOrder() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");
        long productId = createProduct(adminToken, "PO-PRODUCT-" + System.currentTimeMillis());

        MvcResult suggestionsResult = mockMvc.perform(
                        get("/api/purchase-orders/suggestions")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode suggestions = objectMapper.readTree(suggestionsResult.getResponse().getContentAsString());
        boolean foundSuggestion = false;
        for (JsonNode suggestion : suggestions) {
            if (suggestion.path("productId").asLong() == productId) {
                foundSuggestion = true;
                break;
            }
        }
        assertTrue(foundSuggestion, "replenishment suggestion should include created product");

        MvcResult createdResult = mockMvc.perform(
                        post("/api/purchase-orders")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "supplierName", "Demo Supplier",
                                        "note", "integration-test",
                                        "items", List.of(Map.of(
                                                "productId", productId,
                                                "quantity", 15,
                                                "unitCost", 700
                                        ))
                                )))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ORDERED"))
                .andReturn();

        long purchaseOrderId = objectMapper.readTree(createdResult.getResponse().getContentAsString())
                .path("id").asLong();

        mockMvc.perform(
                        post("/api/purchase-orders/{purchaseOrderId}/receive", purchaseOrderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        mockMvc.perform(
                        get("/api/products/{productId}", productId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(15));
    }

    @Test
    void viewerCannotCreateOrReceivePurchaseOrder() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");
        String viewerToken = login("viewer", "viewer123");
        long productId = createProduct(adminToken, "PO-VIEWER-" + System.currentTimeMillis());

        MvcResult createdResult = mockMvc.perform(
                        post("/api/purchase-orders")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "supplierName", "Permission Supplier",
                                        "items", List.of(Map.of(
                                                "productId", productId,
                                                "quantity", 5,
                                                "unitCost", 600
                                        ))
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        long purchaseOrderId = objectMapper.readTree(createdResult.getResponse().getContentAsString())
                .path("id").asLong();

        mockMvc.perform(
                        post("/api/purchase-orders")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "supplierName", "Viewer Supplier",
                                        "items", List.of(Map.of(
                                                "productId", productId,
                                                "quantity", 3,
                                                "unitCost", 500
                                        ))
                                )))
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        post("/api/purchase-orders/{purchaseOrderId}/receive", purchaseOrderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                )
                .andExpect(status().isForbidden());
    }

    private long createProduct(String accessToken, String sku) throws Exception {
        MvcResult created = mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", sku,
                                        "name", "PO Product",
                                        "unitPrice", 1000,
                                        "reorderPoint", 10,
                                        "reorderQuantity", 20
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();
    }

    private String login(String username, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "username", username,
                                        "password", password
                                )))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        return loginJson.path("accessToken").asText();
    }
}
