package com.example.backend.common;

/**
 * ログイン失敗が閾値を超えた場合に返す例外。
 */
public class TooManyLoginAttemptsException extends RuntimeException {

    public TooManyLoginAttemptsException(String message) {
        super(message);
    }
}
