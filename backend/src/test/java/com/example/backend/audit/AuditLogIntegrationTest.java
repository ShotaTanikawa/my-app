package com.example.backend.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.OffsetDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * 主要ユースケースの回帰を守る統合テスト。
 */

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void adminCanReadAuditLogsAndSeeProductCreateAction() throws Exception {
        String adminToken = login("admin", "admin123");
        String sku = "AUDIT-" + System.currentTimeMillis();

        mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", sku,
                                        "name", "Audit Product",
                                        "unitPrice", 1500
                                )))
                )
                .andExpect(status().isCreated());

        MvcResult logsResult = mockMvc.perform(
                        get("/api/audit-logs")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString()).path("items");
        boolean found = false;
        for (JsonNode log : logs) {
            if ("PRODUCT_CREATE".equals(log.path("action").asText())
                    && log.path("detail").asText().contains(sku)) {
                found = true;
                break;
            }
        }

        assertTrue(found, "PRODUCT_CREATE audit log was not found");
    }

    @Test
    void adminCanFilterAuditLogsByAction() throws Exception {
        String adminToken = login("admin", "admin123");

        mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", "AUDIT-FILTER-" + System.currentTimeMillis(),
                                        "name", "Audit Filter Product",
                                        "unitPrice", 1600
                                )))
                )
                .andExpect(status().isCreated());

        MvcResult logsResult = mockMvc.perform(
                        get("/api/audit-logs")
                                .param("action", "PRODUCT_CREATE")
                                .param("size", "20")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = objectMapper.readTree(logsResult.getResponse().getContentAsString());
        JsonNode items = root.path("items");
        assertTrue(items.isArray(), "items should be an array");
        assertTrue(items.size() > 0, "filtered items should not be empty");
        assertTrue(root.path("size").asInt() <= 20, "size should reflect request parameter");

        for (JsonNode item : items) {
            assertEquals("PRODUCT_CREATE", item.path("action").asText());
        }
    }

    @Test
    void nonAdminCannotReadAuditLogs() throws Exception {
        String operatorToken = login("operator", "operator123");

        mockMvc.perform(
                        get("/api/audit-logs")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isForbidden());

        mockMvc.perform(
                        get("/api/audit-logs/export.csv")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanExportAuditLogsAsCsv() throws Exception {
        String adminToken = login("admin", "admin123");

        mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", "AUDIT-CSV-" + System.currentTimeMillis(),
                                        "name", "Audit Csv Product",
                                        "unitPrice", 1700
                                )))
                )
                .andExpect(status().isCreated());

        MvcResult csvResult = mockMvc.perform(
                        get("/api/audit-logs/export.csv")
                                .param("action", "PRODUCT_CREATE")
                                .param("limit", "100")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andReturn();

        String contentType = csvResult.getResponse().getContentType();
        String disposition = csvResult.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        String csvBody = csvResult.getResponse().getContentAsString();

        assertTrue(contentType != null && contentType.startsWith("text/csv"), "content type should be text/csv");
        assertTrue(disposition != null && disposition.contains("audit-logs.csv"), "filename should be set");
        assertTrue(csvBody.startsWith("createdAt,actorUsername,actorRole,action,targetType,targetId,detail"));
        assertTrue(csvBody.contains("\"PRODUCT_CREATE\""), "csv should contain PRODUCT_CREATE action");
    }

    @Test
    void adminCanCleanupOldAuditLogs() throws Exception {
        String adminToken = login("admin", "admin123");
        String sku = "AUDIT-CLEANUP-" + System.currentTimeMillis();

        mockMvc.perform(
                        post("/api/products")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "sku", sku,
                                        "name", "Cleanup Product",
                                        "unitPrice", 1800
                                )))
                )
                .andExpect(status().isCreated());

        int affected = jdbcTemplate.update(
                "UPDATE audit_logs SET created_at = ? WHERE action = ? AND detail LIKE ?",
                OffsetDateTime.now().minusDays(120),
                "PRODUCT_CREATE",
                "%" + sku + "%"
        );
        assertTrue(affected > 0, "test setup failed to mark old logs");

        MvcResult cleanupResult = mockMvc.perform(
                        post("/api/audit-logs/cleanup")
                                .param("retentionDays", "30")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                )
                .andExpect(status().isOk())
                .andReturn();

        JsonNode cleanupJson = objectMapper.readTree(cleanupResult.getResponse().getContentAsString());
        assertEquals(30, cleanupJson.path("retentionDays").asInt());
        assertTrue(cleanupJson.path("deletedCount").asLong() >= affected);

        Integer remaining = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = ? AND detail LIKE ?",
                Integer.class,
                "PRODUCT_CREATE",
                "%" + sku + "%"
        );
        assertEquals(0, remaining);
    }

    @Test
    void nonAdminCannotCleanupAuditLogs() throws Exception {
        String operatorToken = login("operator", "operator123");

        mockMvc.perform(
                        post("/api/audit-logs/cleanup")
                                .param("retentionDays", "30")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken)
                )
                .andExpect(status().isForbidden());
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
