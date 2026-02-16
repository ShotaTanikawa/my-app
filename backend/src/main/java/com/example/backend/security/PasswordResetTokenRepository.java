package com.example.backend.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * パスワード再設定トークンの永続化アクセス。
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    long deleteByExpiresAtBeforeOrUsedAtBefore(OffsetDateTime expiresAt, OffsetDateTime usedAt);
}
