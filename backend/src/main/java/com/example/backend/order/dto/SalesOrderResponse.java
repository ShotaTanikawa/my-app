package com.example.backend.order.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SalesOrderResponse(
        Long id,
        String orderNumber,
        String customerName,
        String status,
        OffsetDateTime createdAt,
        List<SalesOrderItemResponse> items
) {
}
