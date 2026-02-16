package com.example.backend.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 商品カテゴリ作成リクエスト。
 */
public record CreateProductCategoryRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 150) String name,
        Boolean active,
        @Min(0) Integer sortOrder
) {
}

