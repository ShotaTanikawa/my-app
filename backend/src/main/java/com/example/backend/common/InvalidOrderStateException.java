package com.example.backend.common;

public class InvalidOrderStateException extends BusinessRuleException {
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
