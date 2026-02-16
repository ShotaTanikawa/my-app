package com.example.backend.common;
/**
 * 業務ルール違反を表現するドメイン例外。
 */

public class InsufficientStockException extends BusinessRuleException {
    public InsufficientStockException(String message) {
        super(message);
    }
}
