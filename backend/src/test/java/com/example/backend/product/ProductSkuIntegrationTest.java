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
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SKUの正規化と自動採番を検証する統合テスト。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductSkuIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void adminCanGenerateCategoryBasedSequentialSku() throws Exception {
        String adminToken = login("admin", "admin123");
        String categoryCode = "FIG" + (System.currentTimeMillis() % 100000);
        long categoryId = createCategory(adminToken, categoryCode, "SKUカテゴリ");

        String firstSku = requestNextSku(adminToken, categoryId);
        assertThat(firstSku).matches(Pattern.quote(categoryCode) + "-\\d{6}-0001");

        createProduct(adminToken, firstSku, categoryId, "SKU連番テスト商品");

        String secondSku = requestNextSku(adminToken, categoryId);
        assertThat(secondSku).matches(Pattern.quote(categoryCode) + "-\\d{6}-0002");
    }

    @Test
    void createProductNormalizesSkuAndRejectsCaseInsensitiveDuplicate() throws Exception {
        String adminToken = login("admin", "admin123");
        String categoryCode = "NORM" + (System.currentTimeMillis() % 100000);
        long categoryId = createCategory(adminToken, categoryCode, "正規化カテゴリ");

        MvcResult created = mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", "abc-001",
                                        "name", "正規化商品",
                                        "description", "sku normalize test",
                                        "unitPrice", 1300,
                                        "reorderPoint", 2,
                                        "reorderQuantity", 5,
                                        "categoryId", categoryId
                                )))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("ABC-001"))
                .andReturn();

        assertThat(created.getResponse().getContentAsString()).contains("\"sku\":\"ABC-001\"");

        mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", "AbC-001",
                                        "name", "重複商品",
                                        "description", "duplicate",
                                        "unitPrice", 1300,
                                        "reorderPoint", 2,
                                        "reorderQuantity", 5,
                                        "categoryId", categoryId
                                )))
                )
                .andExpect(status().isConflict());
    }

    @Test
    void adminCanApplyCustomSkuRulePerCategory() throws Exception {
        String adminToken = login("admin", "admin123");
        long categoryId = createCategory(adminToken, "RULE" + (System.currentTimeMillis() % 100000), "ルールカテゴリ");

        mockMvc.perform(
                        put("/api/product-categories/{categoryId}/sku-rule", categoryId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "skuPrefix", "FIG-PRO",
                                        "skuSequenceDigits", 5
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skuPrefix").value("FIG-PRO"))
                .andExpect(jsonPath("$.skuSequenceDigits").value(5));

        String firstSku = requestNextSku(adminToken, categoryId);
        assertThat(firstSku).matches("FIG-PRO-\\d{6}-00001");
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

    private String requestNextSku(String token, long categoryId) throws Exception {
        MvcResult result = mockMvc.perform(
                        get("/api/products/sku/next")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .queryParam("categoryId", String.valueOf(categoryId))
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("sku").asText();
    }

    private void createProduct(String token, String sku, long categoryId, String name) throws Exception {
        mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", sku,
                                        "name", name,
                                        "description", "sku create for sequence test",
                                        "unitPrice", 1600,
                                        "reorderPoint", 3,
                                        "reorderQuantity", 10,
                                        "categoryId", categoryId
                                )))
                )
                .andExpect(status().isCreated());
    }
}
