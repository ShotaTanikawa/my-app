package com.example.backend.common;

/**
 * 不正な入力値に対して400を返すための例外。
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
