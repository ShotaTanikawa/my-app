package com.example.backend.sales.dto;

import java.util.List;

/**
 * 売上管理画面向けの集計レスポンス。
 */
public record SalesReportResponse(
        SalesSummaryResponse summary,
        String groupBy,
        List<SalesTrendPointResponse> trends,
        List<SalesLineResponse> lines,
        int lineLimit,
        long totalLineCount
) {
}

