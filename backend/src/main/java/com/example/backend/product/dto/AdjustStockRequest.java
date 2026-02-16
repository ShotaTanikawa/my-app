package com.example.backend.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
/**
 * APIの入力値を受け取るリクエストモデル。
 */

public record AdjustStockRequest(
        @NotNull @Min(1) Integer quantity
) {
}
