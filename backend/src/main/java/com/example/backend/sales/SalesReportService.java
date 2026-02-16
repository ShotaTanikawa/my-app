package com.example.backend.sales;

import com.example.backend.order.OrderStatus;
import com.example.backend.order.SalesOrder;
import com.example.backend.order.SalesOrderItem;
import com.example.backend.order.SalesOrderRepository;
import com.example.backend.sales.dto.SalesLineResponse;
import com.example.backend.sales.dto.SalesReportResponse;
import com.example.backend.sales.dto.SalesSummaryResponse;
import com.example.backend.sales.dto.SalesTrendPointResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 売上集計の業務処理をまとめるサービス。
 */
@Service
public class SalesReportService {

    private static final String METRIC_BASIS = "CONFIRMED_UPDATED_AT";
    private static final ZoneOffset REPORT_ZONE = ZoneOffset.UTC;
    private static final int DEFAULT_LINE_LIMIT = 200;
    private static final int MAX_LINE_LIMIT = 2_000;
    private static final int DEFAULT_EXPORT_LIMIT = 2_000;
    private static final int MAX_EXPORT_LIMIT = 5_000;

    private final SalesOrderRepository salesOrderRepository;

    public SalesReportService(SalesOrderRepository salesOrderRepository) {
        this.salesOrderRepository = salesOrderRepository;
    }

    @Transactional(readOnly = true)
    public SalesReportResponse getSalesReport(
            OffsetDateTime from,
            OffsetDateTime to,
            SalesGroupBy groupBy,
            int lineLimit
    ) {
        SalesGroupBy resolvedGroupBy = groupBy == null ? SalesGroupBy.DAY : groupBy;
        int safeLineLimit = normalizeLimit(lineLimit, DEFAULT_LINE_LIMIT, MAX_LINE_LIMIT);
        List<SalesOrder> confirmedOrders = findConfirmedOrders(from, to);

        BigDecimal totalSalesAmount = BigDecimal.ZERO;
        long totalItemQuantity = 0;
        Map<OffsetDateTime, TrendAggregation> trendMap = new TreeMap<>();
        List<SalesLineResponse> allLines = new ArrayList<>();

        for (SalesOrder order : confirmedOrders) {
            OffsetDateTime soldAt = order.getUpdatedAt();
            BigDecimal orderAmount = BigDecimal.ZERO;
            long orderItemQuantity = 0;

            for (SalesOrderItem item : order.getItems()) {
                BigDecimal lineAmount = item.getUnitPrice()
                        .multiply(BigDecimal.valueOf(item.getQuantity()));
                orderAmount = orderAmount.add(lineAmount);
                orderItemQuantity += item.getQuantity();

                allLines.add(new SalesLineResponse(
                        order.getId(),
                        order.getOrderNumber(),
                        order.getCustomerName(),
                        soldAt,
                        item.getProduct().getId(),
                        item.getProduct().getSku(),
                        item.getProduct().getName(),
                        item.getQuantity(),
                        item.getUnitPrice(),
                        lineAmount
                ));
            }

            totalSalesAmount = totalSalesAmount.add(orderAmount);
            totalItemQuantity += orderItemQuantity;

            OffsetDateTime bucketStart = toBucketStart(soldAt, resolvedGroupBy);
            TrendAggregation aggregation = trendMap.computeIfAbsent(bucketStart, key -> new TrendAggregation());
            aggregation.totalSalesAmount = aggregation.totalSalesAmount.add(orderAmount);
            aggregation.orderCount += 1;
            aggregation.totalItemQuantity += orderItemQuantity;
        }

        allLines.sort(Comparator
                .comparing(SalesLineResponse::soldAt).reversed()
                .thenComparing(SalesLineResponse::orderNumber)
                .thenComparing(SalesLineResponse::sku)
        );

        List<SalesLineResponse> limitedLines = allLines.stream()
                .limit(safeLineLimit)
                .toList();

        long orderCount = confirmedOrders.size();
        BigDecimal averageOrderAmount = orderCount == 0
                ? BigDecimal.ZERO
                : totalSalesAmount.divide(BigDecimal.valueOf(orderCount), 2, RoundingMode.HALF_UP);

        List<SalesTrendPointResponse> trends = trendMap.entrySet().stream()
                .map(entry -> new SalesTrendPointResponse(
                        entry.getKey(),
                        entry.getValue().totalSalesAmount,
                        entry.getValue().orderCount,
                        entry.getValue().totalItemQuantity
                ))
                .toList();

        SalesSummaryResponse summary = new SalesSummaryResponse(
                from,
                to,
                METRIC_BASIS,
                totalSalesAmount,
                orderCount,
                totalItemQuantity,
                averageOrderAmount
        );

        return new SalesReportResponse(
                summary,
                resolvedGroupBy.name(),
                trends,
                limitedLines,
                safeLineLimit,
                allLines.size()
        );
    }

    @Transactional(readOnly = true)
    public List<SalesLineResponse> getSalesLinesForExport(
            OffsetDateTime from,
            OffsetDateTime to,
            Integer limit
    ) {
        int safeLimit = normalizeLimit(limit == null ? DEFAULT_EXPORT_LIMIT : limit, DEFAULT_EXPORT_LIMIT, MAX_EXPORT_LIMIT);
        SalesReportResponse report = getSalesReport(from, to, SalesGroupBy.DAY, safeLimit);
        return report.lines();
    }

    private List<SalesOrder> findConfirmedOrders(OffsetDateTime from, OffsetDateTime to) {
        return salesOrderRepository.findDetailedByStatusAndUpdatedAtBetween(OrderStatus.CONFIRMED, from, to);
    }

    private OffsetDateTime toBucketStart(OffsetDateTime value, SalesGroupBy groupBy) {
        OffsetDateTime utc = value.withOffsetSameInstant(REPORT_ZONE);
        return switch (groupBy) {
            case DAY -> utc.truncatedTo(ChronoUnit.DAYS);
            case WEEK -> utc
                    .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    .truncatedTo(ChronoUnit.DAYS);
            case MONTH -> utc
                    .withDayOfMonth(1)
                    .truncatedTo(ChronoUnit.DAYS);
        };
    }

    private int normalizeLimit(int requested, int fallback, int max) {
        if (requested <= 0) {
            return fallback;
        }
        return Math.min(requested, max);
    }

    private static final class TrendAggregation {
        private BigDecimal totalSalesAmount = BigDecimal.ZERO;
        private long orderCount = 0;
        private long totalItemQuantity = 0;
    }
}

