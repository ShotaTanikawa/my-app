package com.example.backend.purchase.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
/**
 * APIの入力値を受け取るリクエストモデル。
 */

public record CreatePurchaseOrderRequest(
        Long supplierId,
        @Size(max = 150) String supplierName,
        @Size(max = 500) String note,
        @NotEmpty List<@Valid CreatePurchaseOrderItemRequest> items
) {
}
