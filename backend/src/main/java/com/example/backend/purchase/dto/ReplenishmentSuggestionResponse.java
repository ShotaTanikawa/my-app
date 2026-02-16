package com.example.backend.purchase.dto;
/**
 * APIレスポンスとして返す出力モデル。
 */

public record ReplenishmentSuggestionResponse(
        Long productId,
        String sku,
        String productName,
        Integer availableQuantity,
        Integer reservedQuantity,
        Integer reorderPoint,
        Integer reorderQuantity,
        Integer shortageQuantity,
        Integer suggestedQuantity
) {
}
