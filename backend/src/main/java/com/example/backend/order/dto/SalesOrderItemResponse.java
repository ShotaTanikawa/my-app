package com.example.backend.order.dto;

import java.math.BigDecimal;

public record SalesOrderItemResponse(
        Long productId,
        String sku,
        String productName,
        Integer quantity,
        BigDecimal unitPrice
) {
}
