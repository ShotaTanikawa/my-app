package com.example.backend.product.dto;

import java.util.List;

/**
 * 商品一覧のページング結果。
 */
public record ProductPageResponse(
        List<ProductResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious
) {
}

