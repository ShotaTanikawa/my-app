package com.example.backend.sales;

import com.example.backend.sales.dto.SalesLineResponse;
import com.example.backend.sales.dto.SalesReportResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 売上レポート用のAPIを公開するコントローラ。
 */
@RestController
@RequestMapping("/api/sales")
public class SalesReportController {

    private final SalesReportService salesReportService;

    public SalesReportController(SalesReportService salesReportService) {
        this.salesReportService = salesReportService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public SalesReportResponse getSalesReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "DAY") SalesGroupBy groupBy,
            @RequestParam(defaultValue = "200") int lineLimit
    ) {
        return salesReportService.getSalesReport(from, to, groupBy, lineLimit);
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR','VIEWER')")
    public ResponseEntity<String> exportSalesCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @RequestParam(defaultValue = "2000") int limit
    ) {
        List<SalesLineResponse> lines = salesReportService.getSalesLinesForExport(from, to, limit);
        String csv = toCsv(lines);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sales-report.csv\"")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv);
    }

    private String toCsv(List<SalesLineResponse> lines) {
        StringBuilder builder = new StringBuilder();
        builder.append("soldAt,orderNumber,customerName,sku,productName,quantity,unitPrice,lineAmount\n");

        for (SalesLineResponse line : lines) {
            builder.append(escapeCsv(
                            line.soldAt() == null
                                    ? ""
                                    : line.soldAt().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
                    ))
                    .append(',')
                    .append(escapeCsv(line.orderNumber()))
                    .append(',')
                    .append(escapeCsv(line.customerName()))
                    .append(',')
                    .append(escapeCsv(line.sku()))
                    .append(',')
                    .append(escapeCsv(line.productName()))
                    .append(',')
                    .append(escapeCsv(line.quantity() == null ? "" : line.quantity().toString()))
                    .append(',')
                    .append(escapeCsv(line.unitPrice() == null ? "" : line.unitPrice().toPlainString()))
                    .append(',')
                    .append(escapeCsv(line.lineAmount() == null ? "" : line.lineAmount().toPlainString()))
                    .append('\n');
        }

        return builder.toString();
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}

