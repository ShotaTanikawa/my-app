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

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.totalReceivedQuantity").value(15))
                .andExpect(jsonPath("$.totalRemainingQuantity").value(0))
                .andExpect(jsonPath("$.receipts.length()").value(1))
                .andExpect(jsonPath("$.receipts[0].receivedBy").value("operator"))
                .andExpect(jsonPath("$.receipts[0].totalQuantity").value(15))
                .andExpect(jsonPath("$.receipts[0].items.length()").value(1))
                .andExpect(jsonPath("$.receipts[0].items[0].quantity").value(15));

        mockMvc.perform(
                        get("/api/products/{productId}", productId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(15));

        mockMvc.perform(
                        get("/api/purchase-orders/{purchaseOrderId}/receipts/export.csv", purchaseOrderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("\"operator\"")))
                .andExpect(content().string(containsString("\"PO Product\"")));
    }

    @Test
    void operatorCanPartiallyReceivePurchaseOrder() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");
        long productId = createProduct(adminToken, "PO-PARTIAL-" + System.currentTimeMillis());

        MvcResult createdResult = mockMvc.perform(
                        post("/api/purchase-orders")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "supplierName", "Partial Supplier",
                                        "items", List.of(Map.of(
                                                "productId", productId,
                                                "quantity", 20,
                                                "unitCost", 800
                                        ))
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        long purchaseOrderId = objectMapper.readTree(createdResult.getResponse().getContentAsString())
                .path("id").asLong();

        mockMvc.perform(
                        post("/api/purchase-orders/{purchaseOrderId}/receive", purchaseOrderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "items", List.of(Map.of(
                                                "productId", productId,
                                                "quantity", 8
                                        ))
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"))
                .andExpect(jsonPath("$.totalQuantity").value(20))
                .andExpect(jsonPath("$.totalReceivedQuantity").value(8))
                .andExpect(jsonPath("$.totalRemainingQuantity").value(12))
                .andExpect(jsonPath("$.items[0].receivedQuantity").value(8))
                .andExpect(jsonPath("$.items[0].remainingQuantity").value(12))
                .andExpect(jsonPath("$.receipts.length()").value(1))
                .andExpect(jsonPath("$.receipts[0].receivedBy").value("operator"))
                .andExpect(jsonPath("$.receipts[0].totalQuantity").value(8));

        mockMvc.perform(
                        get("/api/products/{productId}", productId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(8));

        mockMvc.perform(
                        post("/api/purchase-orders/{purchaseOrderId}/receive", purchaseOrderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"))
                .andExpect(jsonPath("$.totalReceivedQuantity").value(20))
                .andExpect(jsonPath("$.totalRemainingQuantity").value(0))
                .andExpect(jsonPath("$.receipts.length()").value(2))
                .andExpect(jsonPath("$.receipts[0].totalQuantity").value(12))
                .andExpect(jsonPath("$.receipts[1].totalQuantity").value(8));

        mockMvc.perform(
                        get("/api/products/{productId}", productId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableQuantity").value(20));
    }

    @Test
    void receiptHistoryCanBeFilteredByActorAndDateRange() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");
        long productId = createProduct(adminToken, "PO-RECEIPTS-" + System.currentTimeMillis());

        MvcResult createdResult = mockMvc.perform(
                        post("/api/purchase-orders")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "supplierName", "Receipt Supplier",
                                        "items", List.of(Map.of(
                                                "productId", productId,
                                                "quantity", 10,
                                                "unitCost", 500
                                        ))
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        long purchaseOrderId = objectMapper.readTree(createdResult.getResponse().getContentAsString())
                .path("id").asLong();

        mockMvc.perform(
                        post("/api/purchase-orders/{purchaseOrderId}/receive", purchaseOrderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "items", List.of(Map.of(
                                                "productId", productId,
                                                "quantity", 4
                                        ))
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIALLY_RECEIVED"));

        mockMvc.perform(
                        post("/api/purchase-orders/{purchaseOrderId}/receive", purchaseOrderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "items", List.of(Map.of(
                                                "productId", productId,
                                                "quantity", 6
                                        ))
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RECEIVED"));

        mockMvc.perform(
                        get("/api/purchase-orders/{purchaseOrderId}/receipts", purchaseOrderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .queryParam("receivedBy", "operator")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].receivedBy").value("operator"));

        mockMvc.perform(
                        get("/api/purchase-orders/{purchaseOrderId}/receipts", purchaseOrderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .queryParam("receivedBy", "admin")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].receivedBy").value("admin"));

        String future = OffsetDateTime.now().plusDays(1).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        mockMvc.perform(
                        get("/api/purchase-orders/{purchaseOrderId}/receipts", purchaseOrderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .queryParam("from", future)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
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

    @Test
    void createPurchaseOrderSupportsIdempotencyKey() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");
        long productId = createProduct(adminToken, "PO-IDEMP-" + System.currentTimeMillis());
        String idempotencyKey = "po-create-" + System.currentTimeMillis();

        String payload = objectMapper.writeValueAsString(Map.of(
                "supplierName", "Idempotency Supplier",
                "items", List.of(Map.of(
                        "productId", productId,
                        "quantity", 9,
                        "unitCost", 710
                ))
        ));

        MvcResult first = mockMvc.perform(
                        post("/api/purchase-orders")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult second = mockMvc.perform(
                        post("/api/purchase-orders")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .header("Idempotency-Key", idempotencyKey)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(payload)
                )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode firstBody = objectMapper.readTree(first.getResponse().getContentAsString());
        JsonNode secondBody = objectMapper.readTree(second.getResponse().getContentAsString());
        assertEquals(firstBody.path("id").asLong(), secondBody.path("id").asLong());
        assertEquals(firstBody.path("orderNumber").asText(), secondBody.path("orderNumber").asText());
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
