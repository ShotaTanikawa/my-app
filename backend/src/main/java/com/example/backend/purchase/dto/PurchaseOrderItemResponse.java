package com.example.backend.purchase.dto;

import java.math.BigDecimal;
/**
 * APIレスポンスとして返す出力モデル。
 */

public record PurchaseOrderItemResponse(
        Long productId,
        String sku,
        String productName,
        Integer quantity,
        Integer receivedQuantity,
        Integer remainingQuantity,
        BigDecimal unitCost
) {
}
