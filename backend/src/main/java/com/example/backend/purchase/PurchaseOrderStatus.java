package com.example.backend.purchase;
/**
 * 業務上の状態を型安全に扱う列挙型。
 */

public enum PurchaseOrderStatus {
    ORDERED,
    PARTIALLY_RECEIVED,
    RECEIVED,
    CANCELLED
}
