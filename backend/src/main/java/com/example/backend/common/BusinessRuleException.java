package com.example.backend.common;
/**
 * 業務ルール違反を表現するドメイン例外。
 */

public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
