package com.example.backend.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
/**
 * APIの入力値を受け取るリクエストモデル。
 */

public record CreateSalesOrderRequest(
        @NotBlank @Size(max = 150) String customerName,
        @NotEmpty List<@Valid CreateSalesOrderItemRequest> items
) {
}
