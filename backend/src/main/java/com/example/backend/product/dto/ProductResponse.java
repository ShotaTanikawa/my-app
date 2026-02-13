package com.example.backend.product.dto;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal unitPrice,
        Integer availableQuantity,
        Integer reservedQuantity
) {
}
