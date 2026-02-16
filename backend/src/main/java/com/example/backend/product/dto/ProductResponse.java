package com.example.backend.product.dto;

import java.math.BigDecimal;
/**
 * APIレスポンスとして返す出力モデル。
 */

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal unitPrice,
        Integer reorderPoint,
        Integer reorderQuantity,
        Long categoryId,
        String categoryCode,
        String categoryName,
        Integer availableQuantity,
        Integer reservedQuantity
) {
}
