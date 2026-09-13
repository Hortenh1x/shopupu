package com.example.shopupu.ai.guard;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.config.AiProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AiRequestLimiterTest {
    @Test
    void bothIpAndAccountBudgetsApplyBeforeAnyControllerWork() {
        AiProperties properties = new AiProperties();
        properties.setRequestsPerMinute(1);
        AiRequestLimiter limiter = new AiRequestLimiter(properties);
        limiter.check("192.0.2.1", "account-a");
        assertThrows(AiRateLimitException.class, () -> limiter.check("192.0.2.1", "account-b"));
        assertThrows(AiRateLimitException.class, () -> limiter.check("192.0.2.2", "account-a"));
        assertDoesNotThrow(() -> limiter.check("192.0.2.3", "account-c"));
    }

    @Test
    void boundsTheIdentityMapAndFailsClosedInsteadOfEvictingActiveBudgets() {
        AiProperties properties = new AiProperties();
        properties.setMaxTrackedClients(1);
        AiRequestLimiter limiter = new AiRequestLimiter(properties);
        limiter.check("192.0.2.1", null);
        assertThrows(AiRateLimitException.class, () -> limiter.check("192.0.2.2", null));
    }

    @Test
    void dailyQuotaSurvivesMinuteRolloverAndExpiredIdentitiesCanBeReclaimed() {
        AiProperties properties = new AiProperties();
        properties.setRequestsPerDay(1);
        properties.setMaxTrackedClients(1);
        MutableClock clock = new MutableClock();
        AiRequestLimiter limiter = new AiRequestLimiter(properties, clock);
        limiter.check("192.0.2.1", null);
        clock.now = clock.now.plusSeconds(61);
        assertThrows(AiRateLimitException.class, () -> limiter.check("192.0.2.1", null));
        clock.now = clock.now.plusSeconds(86400);
        assertDoesNotThrow(() -> limiter.check("192.0.2.2", null));
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-12T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
