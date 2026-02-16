package com.example.backend.purchase.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
/**
 * 分納時に指定する明細行ごとの入荷要求。
 */

public record ReceivePurchaseOrderItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity
) {
}
