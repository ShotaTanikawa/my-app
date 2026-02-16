package com.example.backend.security;

import com.example.backend.user.AppUser;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 48;
    private static final int SESSION_ID_BYTE_LENGTH = 18;
    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final int MAX_IP_ADDRESS_LENGTH = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final SecureRandom secureRandom = new SecureRandom();
    private final long refreshExpirationSeconds;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-expiration-seconds:604800}") long refreshExpirationSeconds
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.refreshExpirationSeconds = refreshExpirationSeconds;
    }

    @Transactional
    public String issueToken(AppUser user, DeviceContext deviceContext) {
        return issueToken(user, generateSessionId(), deviceContext);
    }

    @Transactional
    public String issueToken(AppUser user) {
        return issueToken(user, new DeviceContext(null, null));
    }

    @Transactional
    public RotatedToken rotateToken(String rawToken, DeviceContext deviceContext) {
        RefreshToken existing = findActiveToken(rawToken);
        OffsetDateTime now = OffsetDateTime.now();
        existing.setRevoked(true);
        existing.setRevokedAt(now);

        AppUser user = existing.getUser();
        user.getUsername();
        user.getRole();

        String userAgent = normalizedOrFallback(deviceContext.userAgent(), existing.getUserAgent(), MAX_USER_AGENT_LENGTH);
        String ipAddress = normalizedOrFallback(deviceContext.ipAddress(), existing.getIpAddress(), MAX_IP_ADDRESS_LENGTH);
        String refreshedToken = issueToken(user, existing.getSessionId(), new DeviceContext(userAgent, ipAddress));

        return new RotatedToken(user, refreshedToken, existing.getSessionId());
    }

    @Transactional
    public Optional<RevokedSession> revokeToken(String rawToken) {
        String hashed = hash(rawToken);
        return refreshTokenRepository.findByTokenHashAndRevokedFalse(hashed)
                .map(token -> {
                    token.setRevoked(true);
                    token.setRevokedAt(OffsetDateTime.now());

                    AppUser user = token.getUser();
                    user.getUsername();
                    user.getRole();
                    return new RevokedSession(user, token.getSessionId());
                });
    }

    @Transactional(readOnly = true)
    public List<RefreshSession> getActiveSessions(Long userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedFalseOrderByLastUsedAtDesc(userId);

        // ローテーション直後の重複を避けるため、sessionIdごとに最新1件へ集約する。
        LinkedHashMap<String, RefreshSession> bySessionId = new LinkedHashMap<>();
        for (RefreshToken token : activeTokens) {
            bySessionId.computeIfAbsent(token.getSessionId(), ignored ->
                    new RefreshSession(
                            token.getSessionId(),
                            token.getUserAgent(),
                            token.getIpAddress(),
                            token.getCreatedAt(),
                            token.getLastUsedAt(),
                            token.getExpiresAt()
                    )
            );
        }

        return List.copyOf(bySessionId.values());
    }

    @Transactional
    public boolean revokeSession(Long userId, String sessionId) {
        String normalizedSessionId = normalize(sessionId, 64);
        if (normalizedSessionId == null) {
            return false;
        }

        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndSessionIdAndRevokedFalse(userId, normalizedSessionId);
        if (activeTokens.isEmpty()) {
            return false;
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (RefreshToken token : activeTokens) {
            token.setRevoked(true);
            token.setRevokedAt(now);
        }

        return true;
    }

    @Transactional
    public int revokeAllSessions(Long userId) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        if (activeTokens.isEmpty()) {
            return 0;
        }

        OffsetDateTime now = OffsetDateTime.now();
        for (RefreshToken token : activeTokens) {
            token.setRevoked(true);
            token.setRevokedAt(now);
        }
        return activeTokens.size();
    }

    private String issueToken(AppUser user, String sessionId, DeviceContext deviceContext) {
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setSessionId(sessionId);
        refreshToken.setUserAgent(normalize(deviceContext.userAgent(), MAX_USER_AGENT_LENGTH));
        refreshToken.setIpAddress(normalize(deviceContext.ipAddress(), MAX_IP_ADDRESS_LENGTH));
        refreshToken.setExpiresAt(OffsetDateTime.now().plusSeconds(refreshExpirationSeconds));
        refreshToken.setRevoked(false);
        refreshToken.setLastUsedAt(OffsetDateTime.now());
        refreshToken.setRevokedAt(null);

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Scheduled(cron = "${jobs.refresh-token-cleanup-cron:0 0 * * * *}")
    @Transactional
    public void cleanupExpiredTokens() {
        refreshTokenRepository.deleteByExpiresAtBeforeOrRevokedTrue(OffsetDateTime.now());
    }

    private RefreshToken findActiveToken(String rawToken) {
        String hashed = hash(rawToken);
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHashAndRevokedFalse(hashed)
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        if (refreshToken.getExpiresAt().isBefore(OffsetDateTime.now())) {
            refreshToken.setRevoked(true);
            refreshToken.setRevokedAt(OffsetDateTime.now());
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        return refreshToken;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String generateSessionId() {
        byte[] bytes = new byte[SESSION_ID_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return "sess_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizedOrFallback(String value, String fallback, int maxLength) {
        String normalized = normalize(value, maxLength);
        if (normalized != null) {
            return normalized;
        }
        return normalize(fallback, maxLength);
    }

    private String normalize(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private String hash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    public record DeviceContext(String userAgent, String ipAddress) {
    }

    public record RotatedToken(AppUser user, String refreshToken, String sessionId) {
    }

    public record RevokedSession(AppUser user, String sessionId) {
    }

    public record RefreshSession(
            String sessionId,
            String userAgent,
            String ipAddress,
            OffsetDateTime createdAt,
            OffsetDateTime lastUsedAt,
            OffsetDateTime expiresAt
    ) {
    }
}
