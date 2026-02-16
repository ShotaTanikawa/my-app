package com.example.backend.audit.dto;

import java.util.List;

/**
 * 監査ログ一覧をページ単位で返すレスポンスモデル。
 */
public record AuditLogPageResponse(
        List<AuditLogResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}
