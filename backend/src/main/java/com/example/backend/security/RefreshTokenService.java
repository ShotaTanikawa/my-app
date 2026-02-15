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
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTE_LENGTH = 48;

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
    public String issueToken(AppUser user) {
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUser(user);
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setExpiresAt(OffsetDateTime.now().plusSeconds(refreshExpirationSeconds));
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public AppUser rotateToken(String rawToken) {
        RefreshToken existing = findActiveToken(rawToken);
        existing.setRevoked(true);
        AppUser user = existing.getUser();
        // トランザクション内で必要フィールドを初期化してから返す。
        user.getUsername();
        user.getRole();
        return user;
    }

    @Transactional
    public void revokeToken(String rawToken) {
        String hashed = hash(rawToken);
        refreshTokenRepository.findByTokenHashAndRevokedFalse(hashed)
                .ifPresent(token -> token.setRevoked(true));
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
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        return refreshToken;
    }

    private String generateRawToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
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
}
