package com.example.backend.product.dto;

import java.util.List;

/**
 * CSV一括取込の結果サマリ。
 */
public record ProductImportResultResponse(
        int totalRows,
        int successRows,
        int createdRows,
        int updatedRows,
        int failedRows,
        List<ProductImportErrorResponse> errors
) {
}
