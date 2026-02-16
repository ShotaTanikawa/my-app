package com.example.backend.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 冪等キー格納テーブルへアクセスするリポジトリ。
 */
public interface ApiIdempotencyKeyRepository extends JpaRepository<ApiIdempotencyKey, Long> {

    Optional<ApiIdempotencyKey> findByActorUsernameAndEndpointKeyAndIdempotencyKey(
            String actorUsername,
            String endpointKey,
            String idempotencyKey
    );

    long deleteByExpiresAtBefore(OffsetDateTime threshold);
}
