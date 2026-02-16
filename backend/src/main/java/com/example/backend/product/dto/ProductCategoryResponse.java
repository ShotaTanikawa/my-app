package com.example.backend.product.dto;

/**
 * 商品カテゴリのAPI出力モデル。
 */
public record ProductCategoryResponse(
        Long id,
        String code,
        String name,
        Boolean active,
        Integer sortOrder,
        String skuPrefix,
        Integer skuSequenceDigits
) {
}
