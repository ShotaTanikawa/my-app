package com.example.backend.security;

import com.example.backend.audit.AuditLogService;
import com.example.backend.common.BadRequestException;
import com.example.backend.common.BusinessRuleException;
import com.example.backend.ops.AlertNotificationService;
import com.example.backend.user.AppUser;
import com.example.backend.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;

/**
 * パスワード再設定トークンの発行と確定を扱うサービス。
 */
@Service
public class PasswordResetService {

    private static final int TOKEN_BYTE_LENGTH = 48;

    private final AppUserRepository appUserRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final AuditLogService auditLogService;
    private final AlertNotificationService alertNotificationService;

    private final long expirationSeconds;
    private final boolean exposeTokenInResponse;
    private final SecureRandom secureRandom = new SecureRandom();

    public PasswordResetService(
            AppUserRepository appUserRepository,
            PasswordResetTokenRepository tokenRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService,
            AuditLogService auditLogService,
            AlertNotificationService alertNotificationService,
            @Value("${app.auth.password-reset.expiration-seconds:1800}") long expirationSeconds,
            @Value("${app.auth.password-reset.expose-token:false}") boolean exposeTokenInResponse
    ) {
        this.appUserRepository = appUserRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
        this.auditLogService = auditLogService;
        this.alertNotificationService = alertNotificationService;
        this.expirationSeconds = Math.max(300, expirationSeconds);
        this.exposeTokenInResponse = exposeTokenInResponse;
    }

    @Transactional
    public PasswordResetRequestResult requestReset(String username, String clientIp) {
        String normalizedUsername = normalizeUsername(username);

        // アカウント存在可否を隠すため、応答文は常に同じにする。
        AppUser user = appUserRepository.findByUsername(normalizedUsername).orElse(null);
        if (user == null) {
            return new PasswordResetRequestResult(
                    "If the account exists, a password reset instruction has been sent",
                    null
            );
        }

        String rawToken = generateRawToken();
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(OffsetDateTime.now().plusSeconds(expirationSeconds));
        tokenRepository.save(token);

        auditLogService.logAs(
                user.getUsername(),
                user.getRole().name(),
                "AUTH_PASSWORD_RESET_REQUEST",
                "USER",
                user.getId().toString(),
                "password reset requested, ip=" + (clientIp == null ? "-" : clientIp)
        );
        alertNotificationService.notifySecurityEvent(
                "password_reset_request",
                "Password reset requested",
                "username=" + user.getUsername() + ", ip=" + (clientIp == null ? "-" : clientIp)
        );

        return new PasswordResetRequestResult(
                "If the account exists, a password reset instruction has been sent",
                exposeTokenInResponse ? rawToken : null
        );
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        String normalizedToken = normalizeToken(rawToken);
        String normalizedPassword = normalizePassword(newPassword);

        PasswordResetToken token = tokenRepository.findByTokenHash(hash(normalizedToken))
                .orElseThrow(() -> new BadRequestException("Invalid or expired password reset token"));

        OffsetDateTime now = OffsetDateTime.now();
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(now)) {
            throw new BadRequestException("Invalid or expired password reset token");
        }

        AppUser user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(normalizedPassword));
        token.setUsedAt(now);
        int revokedSessions = refreshTokenService.revokeAllSessions(user.getId());

        auditLogService.logAs(
                user.getUsername(),
                user.getRole().name(),
                "AUTH_PASSWORD_RESET_CONFIRM",
                "USER",
                user.getId().toString(),
                "password reset confirmed, revokedSessions=" + revokedSessions
        );
    }

    @Scheduled(cron = "${jobs.password-reset-token-cleanup-cron:0 10 * * * *}")
    @Transactional
    public void cleanupExpiredTokens() {
        OffsetDateTime now = OffsetDateTime.now();
        tokenRepository.deleteByExpiresAtBeforeOrUsedAtBefore(now, now.minusDays(1));
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

    private String normalizeUsername(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("username is required");
        }
        return value.trim();
    }

    private String normalizeToken(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("token is required");
        }
        return value.trim();
    }

    private String normalizePassword(String value) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("newPassword is required");
        }
        String trimmed = value.trim();
        if (trimmed.length() < 8) {
            throw new BusinessRuleException("newPassword must be at least 8 characters");
        }
        return trimmed;
    }

    public record PasswordResetRequestResult(String message, String resetToken) {
    }
}
