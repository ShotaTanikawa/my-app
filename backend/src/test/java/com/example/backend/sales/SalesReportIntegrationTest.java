package com.example.backend.sales;

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 売上集計APIの回帰を守る統合テスト。
 */
@SpringBootTest
@AutoConfigureMockMvc
class SalesReportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void confirmedOrdersAreCountedAndReservedOrdersAreExcluded() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");

        String sku = "SALES-" + System.currentTimeMillis();
        long productId = createProduct(adminToken, sku);
        addStock(adminToken, productId, 50);

        OffsetDateTime from = OffsetDateTime.now();
        OffsetDateTime to = OffsetDateTime.now().plusMinutes(10);

        OrderInfo confirmedOrder = createOrder(operatorToken, productId, 3, "売上確認チームA");
        confirmOrder(operatorToken, confirmedOrder.id());
        createOrder(operatorToken, productId, 2, "売上確認チームB");

        mockMvc.perform(
                        get("/api/sales")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                                .queryParam("from", from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                .queryParam("to", to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                .queryParam("groupBy", "DAY")
                                .queryParam("lineLimit", "50")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.orderCount").value(1))
                .andExpect(jsonPath("$.summary.totalItemQuantity").value(3))
                .andExpect(jsonPath("$.summary.totalSalesAmount").value(6000))
                .andExpect(jsonPath("$.summary.averageOrderAmount").value(6000))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].orderNumber").value(confirmedOrder.orderNumber()))
                .andExpect(jsonPath("$.lines[0].sku").value(sku))
                .andExpect(jsonPath("$.lines[0].quantity").value(3))
                .andExpect(jsonPath("$.lines[0].lineAmount").value(6000));
    }

    @Test
    void viewerCanReadSalesReportAndCsv() throws Exception {
        String adminToken = login("admin", "admin123");
        String operatorToken = login("operator", "operator123");
        String viewerToken = login("viewer", "viewer123");

        String sku = "SALES-VIEW-" + System.currentTimeMillis();
        long productId = createProduct(adminToken, sku);
        addStock(adminToken, productId, 30);

        OffsetDateTime from = OffsetDateTime.now();
        OffsetDateTime to = OffsetDateTime.now().plusMinutes(10);

        OrderInfo confirmedOrder = createOrder(operatorToken, productId, 2, "閲覧テストチーム");
        confirmOrder(operatorToken, confirmedOrder.id());

        mockMvc.perform(
                        get("/api/sales")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                                .queryParam("from", from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                .queryParam("to", to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.orderCount").value(1))
                .andExpect(jsonPath("$.lines[0].orderNumber").value(confirmedOrder.orderNumber()));

        MvcResult csvResult = mockMvc.perform(
                        get("/api/sales/export.csv")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + viewerToken)
                                .queryParam("from", from.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                .queryParam("to", to.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                                .queryParam("limit", "100")
                )
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("soldAt,orderNumber,customerName")))
                .andReturn();

        String body = csvResult.getResponse().getContentAsString();
        assertTrue(body.contains(confirmedOrder.orderNumber()), "csv should include confirmed order number");
        assertTrue(body.contains(sku), "csv should include product sku");
    }

    private long createProduct(String accessToken, String sku) throws Exception {
        MvcResult created = mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", sku,
                                        "name", "Sales Product",
                                        "unitPrice", 2000,
                                        "reorderPoint", 5,
                                        "reorderQuantity", 10
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readTree(created.getResponse().getContentAsString()).path("id").asLong();
    }

    private void addStock(String accessToken, long productId, int quantity) throws Exception {
        mockMvc.perform(
                        post("/api/products/{productId}/stock", productId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("quantity", quantity)))
                )
                .andExpect(status().isOk());
    }

    private OrderInfo createOrder(String accessToken, long productId, int quantity, String customerName) throws Exception {
        MvcResult created = mockMvc.perform(
                        post("/api/orders")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "customerName", customerName,
                                        "items", List.of(Map.of(
                                                "productId", productId,
                                                "quantity", quantity
                                        ))
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(created.getResponse().getContentAsString());
        return new OrderInfo(json.path("id").asLong(), json.path("orderNumber").asText());
    }

    private void confirmOrder(String accessToken, long orderId) throws Exception {
        mockMvc.perform(
                        post("/api/orders/{orderId}/confirm", orderId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                )
                .andExpect(status().isOk());
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

    private record OrderInfo(long id, String orderNumber) {
    }
}
