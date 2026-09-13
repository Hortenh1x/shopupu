package com.example.shopupu.auth.service;

public class AuthRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;
    public AuthRateLimitException(long retryAfterSeconds) {
        super("Too many attempts. Try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }
    public long retryAfterSeconds() { return retryAfterSeconds; }
}
