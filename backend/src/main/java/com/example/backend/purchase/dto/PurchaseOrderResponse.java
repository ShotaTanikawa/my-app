package com.example.backend.purchase.dto;

import java.time.OffsetDateTime;
import java.util.List;
/**
 * APIレスポンスとして返す出力モデル。
 */

public record PurchaseOrderResponse(
        Long id,
        String orderNumber,
        String supplierName,
        String note,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime receivedAt,
        List<PurchaseOrderItemResponse> items
) {
}
