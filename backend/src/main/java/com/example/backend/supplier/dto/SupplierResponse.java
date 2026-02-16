package com.example.backend.supplier.dto;

/**
 * APIレスポンスとして返す出力モデル。
 */
public record SupplierResponse(
        Long id,
        String code,
        String name,
        String contactName,
        String email,
        String phone,
        String note,
        Boolean active
) {
}
