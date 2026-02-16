package com.example.backend.audit;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditLogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

        JsonNode logs = objectMapper.readTree(logsResult.getResponse().getContentAsString());
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
    void nonAdminCannotReadAuditLogs() throws Exception {
        String operatorToken = login("operator", "operator123");

        mockMvc.perform(
                        get("/api/audit-logs")
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
