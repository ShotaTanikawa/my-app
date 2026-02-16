package com.example.backend.audit.dto;

import java.time.OffsetDateTime;

public record AuditLogResponse(
        Long id,
        String actorUsername,
        String actorRole,
        String action,
        String targetType,
        String targetId,
        String detail,
        OffsetDateTime createdAt
) {
}
