package com.example.backend.supplier.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
/**
 * APIの入力値を受け取るリクエストモデル。
 */

public record CreateSupplierRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 150) String name,
        @Size(max = 100) String contactName,
        @Size(max = 150) String email,
        @Size(max = 50) String phone,
        @Size(max = 500) String note
) {
}
