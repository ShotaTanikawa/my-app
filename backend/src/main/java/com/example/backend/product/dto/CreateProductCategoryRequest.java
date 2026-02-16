package com.example.backend.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;

/**
 * 商品カテゴリ作成リクエスト。
 */
public record CreateProductCategoryRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 150) String name,
        @Min(1) Long parentId,
        Boolean active,
        @Min(0) Integer sortOrder,
        @Size(max = 20) String skuPrefix,
        @Min(3) @Max(6) Integer skuSequenceDigits
) {
}
