package com.example.backend.sales.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 売上明細1行分。
 */
public record SalesLineResponse(
        Long orderId,
        String orderNumber,
        String customerName,
        OffsetDateTime soldAt,
        Long productId,
        String sku,
        String productName,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal lineAmount
) {
}

