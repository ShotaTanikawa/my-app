package com.example.backend.supplier.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
/**
 * APIの入力値を受け取るリクエストモデル。
 */

public record UpsertProductSupplierRequest(
        @NotNull Long supplierId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal unitCost,
        @Min(0) Integer leadTimeDays,
        @Min(1) Integer moq,
        @Min(1) Integer lotSize,
        Boolean primary
) {
}
