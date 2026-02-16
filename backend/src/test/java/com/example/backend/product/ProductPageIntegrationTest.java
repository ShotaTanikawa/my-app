package com.example.backend.product;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品一覧ページAPIの検索・絞り込み挙動を守る統合テスト。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductPageIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void productPageSupportsKeywordCategoryAndLowStockFilters() throws Exception {
        String adminToken = login("admin", "admin123");
        String sku = "PAGE-" + System.currentTimeMillis();
        long categoryId = createCategory(adminToken, "CAT-" + System.currentTimeMillis(), "テストカテゴリ");

        createProduct(adminToken, sku, categoryId, 3);

        mockMvc.perform(
                        get("/api/products/page")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .queryParam("q", sku)
                                .queryParam("page", "0")
                                .queryParam("size", "20")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].sku").value(sku))
                .andExpect(jsonPath("$.items[0].categoryId").value(categoryId))
                .andExpect(jsonPath("$.items[0].categoryName").value("テストカテゴリ"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.hasNext").value(false))
                .andExpect(jsonPath("$.hasPrevious").value(false));

        mockMvc.perform(
                        get("/api/products/page")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .queryParam("q", sku)
                                .queryParam("categoryId", String.valueOf(categoryId))
                                .queryParam("lowStockOnly", "true")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].availableQuantity").value(0));
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

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("accessToken")
                .asText();
    }

    private long createCategory(String token, String code, String name) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/product-categories")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "code", code,
                                        "name", name
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("id").asLong();
    }

    private void createProduct(String token, String sku, long categoryId, int reorderPoint) throws Exception {
        mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", sku,
                                        "name", "Page Test Product",
                                        "description", "product page integration test",
                                        "unitPrice", 1200,
                                        "reorderPoint", reorderPoint,
                                        "reorderQuantity", 10,
                                        "categoryId", categoryId
                                )))
                )
                .andExpect(status().isCreated());
    }
}

