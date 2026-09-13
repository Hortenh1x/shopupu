package com.example.shopupu.ai.guard;

public class AiRateLimitException extends RuntimeException {
    private final long retryAfterSeconds;

    public AiRateLimitException(long retryAfterSeconds) {
        super("AI request limit reached. Please try again later.");
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public long retryAfterSeconds() {
        return retryAfterSeconds;
    }
}
