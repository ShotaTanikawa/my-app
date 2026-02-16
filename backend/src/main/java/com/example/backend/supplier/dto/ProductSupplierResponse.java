package com.example.backend.supplier.dto;

import java.math.BigDecimal;
/**
 * APIレスポンスとして返す出力モデル。
 */

public record ProductSupplierResponse(
        Long supplierId,
        String supplierCode,
        String supplierName,
        Boolean supplierActive,
        BigDecimal unitCost,
        Integer leadTimeDays,
        Integer moq,
        Integer lotSize,
        Boolean primary
) {
}
