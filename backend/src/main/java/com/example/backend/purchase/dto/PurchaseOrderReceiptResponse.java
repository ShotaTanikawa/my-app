package com.example.backend.purchase.dto;

import java.time.OffsetDateTime;
import java.util.List;
/**
 * 1回の入荷履歴（ヘッダ+明細）。
 */

public record PurchaseOrderReceiptResponse(
        Long id,
        String receivedBy,
        OffsetDateTime receivedAt,
        Integer totalQuantity,
        List<PurchaseOrderReceiptItemResponse> items
) {
}
