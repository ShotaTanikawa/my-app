package com.example.backend.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
/**
 * ドメインルールと業務処理をまとめるサービス。
 */

@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey signingKey;
    private final List<SecretKey> verificationKeys;
    private final long expirationSeconds;

    public JwtService(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.verify-secrets:}") String verifySecrets,
            @Value("${app.jwt.expiration-seconds:3600}") long expirationSeconds
    ) {
        this.signingKey = toSecretKey(secret, "app.jwt.secret");
        this.verificationKeys = buildVerificationKeys(secret, verifySecrets);
        this.expirationSeconds = expirationSeconds;
    }

    public String generateToken(String username, String role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirationSeconds);

        return Jwts.builder()
                .subject(username)
                .claim("role", role)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public Claims parseClaims(String token) throws JwtException {
        JwtException lastException = null;
        for (SecretKey key : verificationKeys) {
            try {
                return Jwts.parser()
                        .verifyWith(key)
                        .build()
                        .parseSignedClaims(token)
                        .getPayload();
            } catch (JwtException ex) {
                lastException = ex;
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        throw new JwtException("Unable to verify JWT");
    }

    public long getExpirationSeconds() {
        return expirationSeconds;
    }

    private List<SecretKey> buildVerificationKeys(String signingSecret, String verifySecrets) {
        List<SecretKey> keys = new ArrayList<>();
        keys.add(toSecretKey(signingSecret, "app.jwt.secret"));

        Arrays.stream(verifySecrets.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .forEach(secret -> {
                    try {
                        keys.add(toSecretKey(secret, "app.jwt.verify-secrets"));
                    } catch (IllegalArgumentException ex) {
                        log.warn("Skipping invalid JWT verify secret: {}", ex.getMessage());
                    }
                });

        return List.copyOf(keys);
    }

    private SecretKey toSecretKey(String secret, String propertyName) {
        if (secret == null || secret.length() < 32) {
            throw new IllegalArgumentException(propertyName + " must be at least 32 characters long");
        }
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
