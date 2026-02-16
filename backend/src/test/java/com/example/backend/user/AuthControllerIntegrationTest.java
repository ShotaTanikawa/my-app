package com.example.backend.user;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Map;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * 主要ユースケースの回帰を守る統合テスト。
 */

@SpringBootTest(properties = {
        "app.auth.password-reset.expose-token=true",
        "app.alerts.enabled=false"
})
@AutoConfigureMockMvc
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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

    @Test
    void userCanListAndRevokeOwnSession() throws Exception {
        JsonNode loginJson = loginAs("operator", "operator123");
        String accessToken = loginJson.path("accessToken").asText();
        String refreshToken = loginJson.path("refreshToken").asText();

        MvcResult sessionsResult = mockMvc.perform(
                        get("/api/auth/sessions")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sessionId").isString())
                .andReturn();

        JsonNode sessions = objectMapper.readTree(sessionsResult.getResponse().getContentAsString());
        String sessionId = sessions.get(0).path("sessionId").asText();
        assertFalse(sessionId.isBlank(), "session id should not be blank");

        mockMvc.perform(
                        delete("/api/auth/sessions/{sessionId}", sessionId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
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

    @Test
    void passwordResetFlowChangesPassword() throws Exception {
        String username = "reset-user-" + System.currentTimeMillis();
        AppUser user = new AppUser();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode("before-reset-123"));
        user.setRole(UserRole.OPERATOR);
        appUserRepository.save(user);

        MvcResult requestResult = mockMvc.perform(
                        post("/api/auth/password-reset/request")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("username", username)))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").isString())
                .andExpect(jsonPath("$.resetToken").isString())
                .andReturn();

        String resetToken = objectMapper.readTree(requestResult.getResponse().getContentAsString())
                .path("resetToken")
                .asText();

        mockMvc.perform(
                        post("/api/auth/password-reset/confirm")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "token", resetToken,
                                        "newPassword", "after-reset-456"
                                )))
                )
                .andExpect(status().isNoContent());

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "username", username,
                                        "password", "before-reset-123"
                                )))
                )
                .andExpect(status().isUnauthorized());

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "username", username,
                                        "password", "after-reset-456"
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.username").value(username));
    }

    @Test
    void mfaEnabledUserRequiresCodeAtLogin() throws Exception {
        JsonNode loginJson = loginAs("operator", "operator123");
        String accessToken = loginJson.path("accessToken").asText();

        MvcResult setupResult = mockMvc.perform(
                        post("/api/auth/mfa/setup")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.secret").isString())
                .andReturn();

        String secret = objectMapper.readTree(setupResult.getResponse().getContentAsString())
                .path("secret")
                .asText();
        String mfaCode = generateCurrentTotp(secret);

        mockMvc.perform(
                        post("/api/auth/mfa/enable")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("code", mfaCode)))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled").value(true));

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "username", "operator",
                                        "password", "operator123"
                                )))
                )
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("MFA code is invalid or missing"));

        String validCode = generateCurrentTotp(secret);
        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of(
                                        "username", "operator",
                                        "password", "operator123",
                                        "mfaCode", validCode
                                )))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.mfaEnabled").value(true));

        mockMvc.perform(
                        post("/api/auth/mfa/disable")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(Map.of("code", generateCurrentTotp(secret))))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaEnabled").value(false));
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

    private String generateCurrentTotp(String secret) throws Exception {
        byte[] key = decodeBase32(secret);
        long step = Instant.now().getEpochSecond() / 30L;
        byte[] stepBytes = ByteBuffer.allocate(8).putLong(step).array();
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(key, "HmacSHA1"));
        byte[] hmac = mac.doFinal(stepBytes);

        int offset = hmac[hmac.length - 1] & 0x0f;
        int binary = ((hmac[offset] & 0x7f) << 24)
                | ((hmac[offset + 1] & 0xff) << 16)
                | ((hmac[offset + 2] & 0xff) << 8)
                | (hmac[offset + 3] & 0xff);
        int code = binary % 1_000_000;
        return String.format(Locale.ROOT, "%06d", code);
    }

    private byte[] decodeBase32(String secret) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String normalized = secret.trim().replace("-", "").replace(" ", "").toUpperCase(Locale.ROOT);
        ByteBuffer output = ByteBuffer.allocate((normalized.length() * 5) / 8 + 1);

        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < normalized.length(); i++) {
            int index = alphabet.indexOf(normalized.charAt(i));
            if (index < 0) {
                throw new IllegalArgumentException("Invalid base32 secret");
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                output.put((byte) ((buffer >> bitsLeft) & 0xff));
            }
        }

        byte[] bytes = new byte[output.position()];
        output.flip();
        output.get(bytes);
        return bytes;
    }
}
