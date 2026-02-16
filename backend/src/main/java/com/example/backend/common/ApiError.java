package com.example.backend.common;

import java.time.OffsetDateTime;
/**
 * エラー応答で返却する標準的なエラーペイロード。
 */

public record ApiError(
        OffsetDateTime timestamp,
        int status,
        String error,
        String message,
        String path
) {
}
