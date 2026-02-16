package com.example.backend.common;
/**
 * 業務ルール違反を表現するドメイン例外。
 */

public class InvalidOrderStateException extends BusinessRuleException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
