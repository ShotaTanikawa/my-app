package com.example.backend.user;

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

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void loginThenGetMeReturnsAuthenticatedUser() throws Exception {
        JsonNode loginJson = loginAs("operator", "operator123");
        String accessToken = loginJson.path("accessToken").asText();

        mockMvc.perform(
                        get("/api/auth/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("operator"))
                .andExpect(jsonPath("$.role").value("OPERATOR"));
    }

    @Test
    void loginWithWrongPasswordReturnsUnauthorized() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "operator",
                "password", "wrong-password"
        ));

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    void refreshTokenRotatesAndOldRefreshTokenBecomesInvalid() throws Exception {
        JsonNode loginJson = loginAs("operator", "operator123");
        String oldRefreshToken = loginJson.path("refreshToken").asText();

        MvcResult refreshResult = mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefreshToken)))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.user.username").value("operator"))
                .andReturn();

        JsonNode refreshedJson = objectMapper.readTree(refreshResult.getResponse().getContentAsString());
        String newAccessToken = refreshedJson.path("accessToken").asText();

        mockMvc.perform(
                        get("/api/auth/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + newAccessToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("operator"));

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("refreshToken", oldRefreshToken)))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        JsonNode loginJson = loginAs("operator", "operator123");
        String refreshToken = loginJson.path("refreshToken").asText();

        mockMvc.perform(
                        post("/api/auth/logout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken)))
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken)))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired refresh token"));
    }

    private JsonNode loginAs(String username, String password) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", username,
                "password", password
        ));

        MvcResult loginResult = mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.user.username").value(username))
                .andReturn();

        return objectMapper.readTree(loginResult.getResponse().getContentAsString());
    }
}
