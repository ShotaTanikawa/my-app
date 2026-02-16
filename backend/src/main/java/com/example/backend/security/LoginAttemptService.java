package com.example.backend.security;

import com.example.backend.common.TooManyLoginAttemptsException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 短時間の連続ログイン失敗を制限して総当たり攻撃を抑止するサービス。
 */
@Service
public class LoginAttemptService {

    private final int maxFailures;
    private final long windowSeconds;
    private final long lockSeconds;

    private final Map<String, AttemptState> attempts = new ConcurrentHashMap<>();

    public LoginAttemptService(
            @Value("${app.auth.login-attempt.max-failures:5}") int maxFailures,
            @Value("${app.auth.login-attempt.window-seconds:900}") long windowSeconds,
            @Value("${app.auth.login-attempt.lock-seconds:900}") long lockSeconds
    ) {
        this.maxFailures = Math.max(1, maxFailures);
        this.windowSeconds = Math.max(60, windowSeconds);
        this.lockSeconds = Math.max(60, lockSeconds);
    }

    public void checkAllowed(String username, String ipAddress) {
        String key = toKey(username, ipAddress);
        AttemptState state = attempts.get(key);
        if (state == null) {
            return;
        }

        Instant now = Instant.now();
        if (state.lockedUntil() != null && state.lockedUntil().isAfter(now)) {
            long secondsLeft = Math.max(1, state.lockedUntil().getEpochSecond() - now.getEpochSecond());
            throw new TooManyLoginAttemptsException(
                    "Too many failed login attempts. Retry after " + secondsLeft + " seconds"
            );
        }

        if (state.firstFailedAt() != null && state.firstFailedAt().plusSeconds(windowSeconds).isBefore(now)) {
            attempts.remove(key);
        }
    }

    public void recordFailure(String username, String ipAddress) {
        String key = toKey(username, ipAddress);
        Instant now = Instant.now();

        attempts.compute(key, (ignored, current) -> {
            if (current == null
                    || current.firstFailedAt() == null
                    || current.firstFailedAt().plusSeconds(windowSeconds).isBefore(now)) {
                return new AttemptState(1, now, null, now);
            }

            int failures = current.failures() + 1;
            Instant lockedUntil = failures >= maxFailures ? now.plusSeconds(lockSeconds) : current.lockedUntil();
            return new AttemptState(failures, current.firstFailedAt(), lockedUntil, now);
        });
    }

    public void recordSuccess(String username, String ipAddress) {
        attempts.remove(toKey(username, ipAddress));
    }

    @Scheduled(cron = "${jobs.login-attempt-cleanup-cron:0 */15 * * * *}")
    public void cleanup() {
        Instant now = Instant.now();
        attempts.entrySet().removeIf(entry -> {
            AttemptState state = entry.getValue();
            Instant lastUpdatedAt = state.lastUpdatedAt();
            if (lastUpdatedAt == null) {
                return true;
            }
            return lastUpdatedAt.plusSeconds(Math.max(windowSeconds, lockSeconds) * 2).isBefore(now);
        });
    }

    private String toKey(String username, String ipAddress) {
        String normalizedUser = username == null ? "-" : username.trim().toLowerCase(Locale.ROOT);
        String normalizedIp = ipAddress == null ? "-" : ipAddress.trim();
        return normalizedUser + "|" + normalizedIp;
    }

    private record AttemptState(
            int failures,
            Instant firstFailedAt,
            Instant lockedUntil,
            Instant lastUpdatedAt
    ) {
    }
}
