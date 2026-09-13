package com.example.shopupu.auth.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/** Bounded fixed windows for the single-instance demo; misses do not extend a lockout. */
@Component
public class AccountAuthThrottle {
    private final Clock clock;
    public AccountAuthThrottle() { this(Clock.systemUTC()); }
    AccountAuthThrottle(Clock clock) { this.clock = clock; }
    private record Window(long expiresAt, AtomicInteger attempts) {}
    private final Cache<String, Window> attempts = Caffeine.newBuilder()
            .maximumSize(10_000).expireAfterWrite(Duration.ofMinutes(15)).build();

    public void check(String operation, String account) {
        int limit = operation.equals("login") ? 10 : 5;
        long windowMillis = operation.equals("login") ? 60_000 : 900_000;
        long now = clock.millis();
        String key = operation + ":" + OneTimeTokenService.hash(account.trim().toLowerCase(Locale.ROOT));
        Window window = attempts.asMap().compute(key, (ignored, existing) ->
                existing == null || existing.expiresAt() <= now
                        ? new Window(now + windowMillis, new AtomicInteger()) : existing);
        if (window.attempts().incrementAndGet() > limit) {
            throw new AuthRateLimitException(Math.max(1, (window.expiresAt() - now + 999) / 1000));
        }
    }
}
