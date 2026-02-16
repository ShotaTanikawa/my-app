package com.example.backend.sales.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 売上サマリーを返す出力モデル。
 */
public record SalesSummaryResponse(
        OffsetDateTime from,
        OffsetDateTime to,
        String metricBasis,
        BigDecimal totalSalesAmount,
        long orderCount,
        long totalItemQuantity,
        BigDecimal averageOrderAmount
) {
}

