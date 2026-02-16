package com.example.backend.security;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
/**
 * JPA経由で永続化データへアクセスするリポジトリ。
 */

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    List<RefreshToken> findByUserIdAndRevokedFalseOrderByLastUsedAtDesc(Long userId);

    List<RefreshToken> findByUserIdAndSessionIdAndRevokedFalse(Long userId, String sessionId);

    List<RefreshToken> findByUserIdAndRevokedFalse(Long userId);

    void deleteByExpiresAtBeforeOrRevokedTrue(OffsetDateTime now);
}
