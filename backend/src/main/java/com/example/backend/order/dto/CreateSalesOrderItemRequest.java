package com.example.backend.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateSalesOrderItemRequest(
        @NotNull Long productId,
        @NotNull @Positive Integer quantity
) {
}
