package com.example.backend.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
/**
 * APIの入力値を受け取るリクエストモデル。
 */

public record CreateSalesOrderItemRequest(
        @NotNull Long productId,
        @NotNull @Positive Integer quantity
) {
}
