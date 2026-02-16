package com.example.backend.purchase.dto;

import jakarta.validation.Valid;

import java.util.List;
/**
 * 入荷処理リクエスト。未指定時は未入荷残数を全量受領する。
 */

public record ReceivePurchaseOrderRequest(
        List<@Valid ReceivePurchaseOrderItemRequest> items
) {
}
