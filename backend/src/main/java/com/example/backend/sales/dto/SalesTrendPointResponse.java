package com.example.backend.sales.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 期間別の売上推移1ポイント。
 */
public record SalesTrendPointResponse(
        OffsetDateTime bucketStart,
        BigDecimal totalSalesAmount,
        long orderCount,
        long totalItemQuantity
) {
}

