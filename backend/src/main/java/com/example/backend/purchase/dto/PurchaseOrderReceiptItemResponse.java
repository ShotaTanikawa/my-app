package com.example.backend.purchase.dto;
/**
 * 1回の入荷で記録された商品別数量。
 */

public record PurchaseOrderReceiptItemResponse(
        Long productId,
        String sku,
        String productName,
        Integer quantity
) {
}
