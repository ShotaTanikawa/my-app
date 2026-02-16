package com.example.backend.product.dto;

/**
 * CSV取込時の行エラー情報。
 */
public record ProductImportErrorResponse(
        int rowNumber,
        String message
) {
}
