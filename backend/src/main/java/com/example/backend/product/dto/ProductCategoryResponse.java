package com.example.backend.product.dto;

/**
 * 商品カテゴリのAPI出力モデル。
 */
public record ProductCategoryResponse(
        Long id,
        String code,
        String name,
        Long parentId,
        String parentCode,
        String parentName,
        Integer depth,
        String pathName,
        Boolean active,
        Integer sortOrder,
        String skuPrefix,
        Integer skuSequenceDigits
) {
}
