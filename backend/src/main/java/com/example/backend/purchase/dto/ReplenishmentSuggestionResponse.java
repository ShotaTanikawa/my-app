package com.example.backend.purchase.dto;

import java.math.BigDecimal;
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
        Integer suggestedQuantity,
        Long suggestedSupplierId,
        String suggestedSupplierCode,
        String suggestedSupplierName,
        BigDecimal suggestedUnitCost,
        Integer leadTimeDays,
        Integer moq,
        Integer lotSize
) {
}
