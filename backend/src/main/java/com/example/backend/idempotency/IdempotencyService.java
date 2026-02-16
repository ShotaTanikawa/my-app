package com.example.backend.idempotency;

import com.example.backend.common.BusinessRuleException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.function.Supplier;

/**
 * APIの二重実行を防止する冪等処理サービス。
 */
@Service
public class IdempotencyService {

    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final ApiIdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final long ttlSeconds;

    public IdempotencyService(
            ApiIdempotencyKeyRepository repository,
            ObjectMapper objectMapper,
            @Value("${app.idempotency.enabled:true}") boolean enabled,
            @Value("${app.idempotency.ttl-seconds:86400}") long ttlSeconds
    ) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.ttlSeconds = ttlSeconds;
    }

    @Transactional
    public <T> T execute(
            String actorUsername,
            String endpointKey,
            String idempotencyKey,
            Class<T> responseType,
            Supplier<T> action
    ) {
        if (!enabled) {
            return action.get();
        }

        String normalizedKey = normalizeIdempotencyKey(idempotencyKey);
        if (normalizedKey == null) {
            return action.get();
        }

        OffsetDateTime now = OffsetDateTime.now();
        var existing = repository.findByActorUsernameAndEndpointKeyAndIdempotencyKey(
                actorUsername,
                endpointKey,
                normalizedKey
        );
        if (existing.isPresent()) {
            ApiIdempotencyKey key = existing.get();
            if (key.getExpiresAt().isAfter(now)) {
                return deserialize(key.getResponseBody(), responseType);
            }
            repository.delete(key);
        }

        T response = action.get();
        String responseBody = serialize(response);

        ApiIdempotencyKey key = new ApiIdempotencyKey();
        key.setActorUsername(actorUsername);
        key.setEndpointKey(endpointKey);
        key.setIdempotencyKey(normalizedKey);
        key.setResponseBody(responseBody);
        key.setExpiresAt(now.plusSeconds(Math.max(60, ttlSeconds)));

        try {
            repository.save(key);
        } catch (DataIntegrityViolationException ex) {
            // 同時リクエストで先に保存された場合は既存結果を返す。
            ApiIdempotencyKey saved = repository.findByActorUsernameAndEndpointKeyAndIdempotencyKey(
                            actorUsername,
                            endpointKey,
                            normalizedKey
                    )
                    .orElseThrow(() -> ex);
            return deserialize(saved.getResponseBody(), responseType);
        }

        return response;
    }

    @Scheduled(cron = "${jobs.idempotency-cleanup-cron:0 */30 * * * *}")
    @Transactional
    public void cleanupExpiredKeys() {
        repository.deleteByExpiresAtBefore(OffsetDateTime.now());
    }

    private String normalizeIdempotencyKey(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BusinessRuleException(
                    "Idempotency-Key must be " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters or less"
            );
        }
        return normalized;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize idempotent response", ex);
        }
    }

    private <T> T deserialize(String json, Class<T> responseType) {
        try {
            return objectMapper.readValue(json, responseType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize idempotent response", ex);
        }
    }
}
