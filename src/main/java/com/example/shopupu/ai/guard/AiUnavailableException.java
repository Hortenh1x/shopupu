package com.example.shopupu.ai.guard;

/** Provider details, URLs, payloads and credentials must never be propagated. */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException() {
        super("AI assistance is temporarily unavailable");
    }
}
