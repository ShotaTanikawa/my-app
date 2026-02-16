package com.example.backend.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * カテゴリのSKUルール更新リクエスト。
 */
public record UpdateCategorySkuRuleRequest(
        @Size(max = 20) String skuPrefix,
        @Min(3) @Max(6) Integer skuSequenceDigits
) {
}
