package com.example.backend.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
/**
 * APIの入力値を受け取るリクエストモデル。
 */

public record UpdateProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 500) String description,
        @NotNull @DecimalMin(value = "0.01") BigDecimal unitPrice,
        @Min(0) Integer reorderPoint,
        @Min(0) Integer reorderQuantity,
        @Min(1) Long categoryId
) {
}
