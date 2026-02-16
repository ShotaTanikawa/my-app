package com.example.backend.product;

import com.example.backend.inventory.Inventory;
import com.example.backend.inventory.InventoryRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 商品CSV一括取込の挙動を担保する統合テスト。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Test
    void adminCanImportProductsCsvWithCreateAndUpdate() throws Exception {
        String adminToken = login("admin", "admin123");
        String categoryCode = "IMP-" + System.currentTimeMillis();
        long categoryId = createCategory(adminToken, categoryCode, "取込カテゴリ");

        String existingSku = "IMP-EXIST-" + System.currentTimeMillis();
        long existingProductId = createProduct(adminToken, existingSku, categoryId, "更新前商品");
        String newSku = "IMP-NEW-" + System.currentTimeMillis();

        String csv = String.join("\n",
                "sku,name,categoryCode,unitPrice,availableQuantity,description",
                existingSku + ",更新後商品," + categoryCode + ",1500,12,updated by csv",
                newSku + ",新規商品," + categoryCode + ",2300,7,new by csv"
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "products.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                        multipart("/api/products/import")
                                .file(file)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.successRows").value(2))
                .andExpect(jsonPath("$.createdRows").value(1))
                .andExpect(jsonPath("$.updatedRows").value(1))
                .andExpect(jsonPath("$.failedRows").value(0));

        Product updatedProduct = productRepository.findWithCategoryBySku(existingSku).orElseThrow();
        assertThat(updatedProduct.getName()).isEqualTo("更新後商品");
        assertThat(updatedProduct.getDescription()).isEqualTo("updated by csv");
        assertThat(updatedProduct.getUnitPrice()).isEqualByComparingTo("1500");
        assertThat(updatedProduct.getCategory()).isNotNull();
        assertThat(updatedProduct.getCategory().getId()).isEqualTo(categoryId);

        Inventory updatedInventory = inventoryRepository.findByProductId(existingProductId).orElseThrow();
        assertThat(updatedInventory.getAvailableQuantity()).isEqualTo(12);

        Product createdProduct = productRepository.findWithCategoryBySku(newSku).orElseThrow();
        assertThat(createdProduct.getName()).isEqualTo("新規商品");
        assertThat(createdProduct.getDescription()).isEqualTo("new by csv");
        assertThat(createdProduct.getCategory()).isNotNull();
        assertThat(createdProduct.getCategory().getId()).isEqualTo(categoryId);
        Inventory createdInventory = inventoryRepository.findByProductId(createdProduct.getId()).orElseThrow();
        assertThat(createdInventory.getAvailableQuantity()).isEqualTo(7);
    }

    @Test
    void importReturnsRowErrorsAndContinues() throws Exception {
        String adminToken = login("admin", "admin123");
        String okSku = "IMP-OK-" + System.currentTimeMillis();

        String csv = String.join("\n",
                "sku,name,unitPrice,availableQuantity,description",
                okSku + ",正常行,1200,5,ok",
                "IMP-NG-" + System.currentTimeMillis() + ",異常行,abc,3,invalid price"
        );
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "products.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(
                        multipart("/api/products/import")
                                .file(file)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.successRows").value(1))
                .andExpect(jsonPath("$.createdRows").value(1))
                .andExpect(jsonPath("$.updatedRows").value(0))
                .andExpect(jsonPath("$.failedRows").value(1))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("errors").isArray()).isTrue();
        assertThat(body.path("errors").size()).isEqualTo(1);
        assertThat(body.path("errors").get(0).path("rowNumber").asInt()).isEqualTo(3);
        assertThat(productRepository.findBySku(okSku)).isPresent();
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

    private long createProduct(String token, String sku, long categoryId, String name) throws Exception {
        MvcResult result = mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", sku,
                                        "name", name,
                                        "description", "before import",
                                        "unitPrice", 1000,
                                        "reorderPoint", 3,
                                        "reorderQuantity", 10,
                                        "categoryId", categoryId
                                )))
                )
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("id").asLong();
    }
}
