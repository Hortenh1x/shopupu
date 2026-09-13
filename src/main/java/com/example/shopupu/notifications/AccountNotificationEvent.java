package com.example.shopupu.notifications;

/** In-memory post-commit work; secrets never go into logs or API responses. */
public record AccountNotificationEvent(Kind kind, String email, String token) {
    public enum Kind { PASSWORD_RESET, EMAIL_VERIFICATION }
    @Override public String toString() { return "AccountNotificationEvent[kind=" + kind + ", redacted]"; }
}
