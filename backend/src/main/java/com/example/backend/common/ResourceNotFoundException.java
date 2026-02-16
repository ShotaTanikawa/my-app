package com.example.backend.common;
/**
 * 業務ルール違反を表現するドメイン例外。
 */

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
