package com.example.shopupu.ai.guard;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.config.AiProperties;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class AiUsageGuardTest {
    private final AiProperties properties = new AiProperties();
    private final MutableClock clock = new MutableClock();

    @Test
    void disabledAndOversizedInputsCannotReserveACall() {
        var guard = new AiUsageGuard(properties, clock);
        assertNull(guard.tryAcquire(List.of("hello"), 100));
        properties.setEnabled(true);
        assertNull(guard.tryAcquire(List.of("x".repeat(properties.getMaxInputBytes() + 1)), 100));
    }

    @Test
    void reservesConcurrencyBeforeWorkAndReleasesOnlyOnce() {
        properties.setEnabled(true);
        properties.setMaxConcurrentCalls(1);
        var guard = new AiUsageGuard(properties, clock);
        var permit = guard.tryAcquire(List.of("hello"), 100);
        assertNotNull(permit);
        assertNull(guard.tryAcquire(List.of("another"), 100));
        permit.close();
        permit.close();
        var next = guard.tryAcquire(List.of("another"), 100);
        assertNotNull(next);
        assertNull(guard.tryAcquire(List.of("overlap"), 100));
        next.close();
    }

    @Test
    void reservesDailyCallsEvenWhenProviderFailsAndMinuteResets() {
        properties.setEnabled(true);
        properties.setExternalCallsPerMinute(1);
        properties.setExternalCallsPerDay(2);
        var guard = new AiUsageGuard(properties, clock);
        guard.tryAcquire(List.of("one"), 100).close();
        assertNull(guard.tryAcquire(List.of("two"), 100));
        clock.advance(61);
        guard.tryAcquire(List.of("two"), 100).close();
        clock.advance(61);
        assertNull(guard.tryAcquire(List.of("three"), 100));
        clock.advance(86_400);
        assertNotNull(guard.tryAcquire(List.of("new day"), 100));
    }

    @Test
    void tokenReservationIncludesInputAndMaximumOutput() {
        properties.setEnabled(true);
        properties.setExternalTokensPerMinute(1000L);
        properties.setExternalTokensPerDay(1000L);
        var guard = new AiUsageGuard(properties, clock);
        assertNull(guard.tryAcquire(List.of("x".repeat(800)), 800));
        try (var permit = guard.tryAcquire(List.of("small"), 100)) {
            assertNotNull(permit);
        }
    }

    @Test
    void rejectsCallsInsideADatabaseTransaction() {
        properties.setEnabled(true);
        var guard = new AiUsageGuard(properties, clock);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertNull(guard.tryAcquire(List.of("not inside transaction"), 100));
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void simultaneousReservationsNeverExceedGlobalConcurrency() throws Exception {
        properties.setEnabled(true);
        properties.setMaxConcurrentCalls(2);
        var guard = new AiUsageGuard(properties, clock);
        var attempted = new CountDownLatch(8);
        var release = new CountDownLatch(1);
        var accepted = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(8)) {
            List<Future<?>> tasks = new ArrayList<>();
            for (int i = 0; i < 8; i++) tasks.add(executor.submit(() -> {
                try (var permit = guard.tryAcquire(List.of("synthetic"), 100)) {
                    if (permit != null) accepted.incrementAndGet();
                    attempted.countDown();
                    assertTrue(release.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(exception);
                }
            }));
            try {
                assertTrue(attempted.await(5, TimeUnit.SECONDS));
                assertEquals(2, accepted.get());
            } finally {
                release.countDown();
            }
            for (var task : tasks) task.get(5, TimeUnit.SECONDS);
        }
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-09-12T12:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
