package com.example.backend.purchase.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
/**
 * APIの入力値を受け取るリクエストモデル。
 */

public record CreatePurchaseOrderItemRequest(
        @NotNull Long productId,
        @NotNull @Min(1) Integer quantity,
        @NotNull @DecimalMin(value = "0.01") BigDecimal unitCost
) {
}
