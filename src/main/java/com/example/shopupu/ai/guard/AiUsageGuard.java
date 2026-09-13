package com.example.shopupu.ai.guard;

import com.example.shopupu.config.AiProperties;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Atomic, conservative reservations for every external AI provider in this JVM. */
@Component
public class AiUsageGuard {
    private final AiProperties properties;
    private final Clock clock;
    private long minute = -1;
    private long day = -1;
    private int minuteCalls;
    private int dayCalls;
    private long minuteTokens;
    private long dayTokens;
    private int active;

    @Autowired
    public AiUsageGuard(AiProperties properties) {
        this(properties, Clock.systemUTC());
    }

    AiUsageGuard(AiProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Null means disabled, exhausted, oversized or an invalid transaction boundary. No waiting queue. */
    public synchronized Permit tryAcquire(List<String> inputs, int maxOutputTokens) {
        if (!properties.isEnabled() || TransactionSynchronizationManager.isActualTransactionActive()
                || inputs == null || inputs.isEmpty() || inputs.size() > 64
                || maxOutputTokens < 0 || maxOutputTokens > properties.getMaxOutputTokens()) {
            return null;
        }
        long bytes = 0;
        for (String input : inputs) {
            if (input == null || input.length() > properties.getMaxInputBytes()) return null;
            bytes += input.getBytes(StandardCharsets.UTF_8).length;
            if (bytes > properties.getMaxInputBytes()) return null;
        }
        // One UTF-8 byte per token is intentionally conservative for the fixed providers.
        // Charge the full output cap and message framing; failures are never refunded.
        long reservedTokens = bytes + 512L + 64L * inputs.size() + maxOutputTokens;
        long now = clock.instant().getEpochSecond();
        if (minute != now / 60) {
            minute = now / 60;
            minuteCalls = 0;
            minuteTokens = 0;
        }
        if (day != now / 86400) {
            day = now / 86400;
            dayCalls = 0;
            dayTokens = 0;
        }
        if (active >= properties.getMaxConcurrentCalls()
                || minuteCalls >= properties.getExternalCallsPerMinute()
                || dayCalls >= properties.getExternalCallsPerDay()
                || reservedTokens > properties.getExternalTokensPerMinute() - minuteTokens
                || reservedTokens > properties.getExternalTokensPerDay() - dayTokens) {
            return null;
        }
        active++;
        minuteCalls++;
        dayCalls++;
        minuteTokens += reservedTokens;
        dayTokens += reservedTokens;
        return new Permit();
    }

    public final class Permit implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            synchronized (AiUsageGuard.this) {
                if (!closed) {
                    closed = true;
                    active--;
                }
            }
        }
    }
}
