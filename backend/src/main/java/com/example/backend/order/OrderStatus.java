package com.example.backend.order;
/**
 * 業務上の状態を型安全に扱う列挙型。
 */

public enum OrderStatus {
    RESERVED,
    CONFIRMED,
    CANCELLED
}
