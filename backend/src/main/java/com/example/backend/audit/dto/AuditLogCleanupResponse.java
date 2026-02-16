package com.example.backend.audit.dto;

import java.time.OffsetDateTime;

// 監査ログクリーンアップ結果を返すAPIレスポンス。
public record AuditLogCleanupResponse(
        long deletedCount,
        int retentionDays,
        OffsetDateTime cutoff,
        OffsetDateTime executedAt
) {
}
