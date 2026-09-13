package com.example.shopupu.notifications;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.config.NotificationProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class NotificationDeliveryTest {
    private NotificationService fake(boolean available) { return new NotificationService() {
        public boolean isAvailable() { return available; }
        public void sendPasswordReset(String email, String token) {}
        public void sendEmailVerification(String email, String token) {}
        public void sendOrderStatusUpdate(String email, String order, String status) {}
    }; }
    @Test void failedSenderIsRetriedAndNeverRecordedAsAccepted() {
        var props = new NotificationProperties(); props.setMaxAttempts(2);
        var meters = new SimpleMeterRegistry(); var attempts = new AtomicInteger();
        new NotificationDelivery(fake(true), props, meters, Runnable::run).deliver("PASSWORD_RESET", () -> {
            attempts.incrementAndGet(); throw new IllegalStateException("provider failure");
        });
        assertEquals(2, attempts.get());
        assertEquals(1, meters.get("shopupu.notification.delivery").tag("outcome", "failed").counter().count());
        assertNull(meters.find("shopupu.notification.delivery").tag("outcome", "accepted").counter());
    }
    @Test void disabledSenderNeverInvokesAction() {
        var attempts = new AtomicInteger();
        new NotificationDelivery(fake(false), new NotificationProperties(), new SimpleMeterRegistry(), Runnable::run)
                .deliver("PASSWORD_RESET", attempts::incrementAndGet);
        assertEquals(0, attempts.get());
    }
    @Test void transientFailureCanBeRetriedSuccessfully() {
        var meters = new SimpleMeterRegistry(); var attempts = new AtomicInteger();
        new NotificationDelivery(fake(true), new NotificationProperties(), meters, Runnable::run).deliver("PASSWORD_RESET", () -> {
            if (attempts.incrementAndGet() == 1) throw new IllegalStateException();
        });
        assertEquals(2, attempts.get());
        assertEquals(1, meters.get("shopupu.notification.delivery").tag("outcome", "accepted").counter().count());
    }
    @Test void requestLanguageReachesTheWorkerThreadAndIsResetAfterwards() throws Exception {
        var seen = new java.util.concurrent.atomic.AtomicReference<java.util.Locale>();
        var worker = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var delivery = new NotificationDelivery(fake(true), new NotificationProperties(), new SimpleMeterRegistry(), worker);
            org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.GERMAN);
            try { delivery.enqueue("PASSWORD_RESET", () -> seen.set(com.example.shopupu.common.i18n.SupportedLocales.current())); }
            finally { org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext(); }
            worker.submit(() -> {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(java.util.Locale.GERMAN, seen.get());
            var afterwards = worker.submit(com.example.shopupu.common.i18n.SupportedLocales::current).get(5, java.util.concurrent.TimeUnit.SECONDS);
            assertEquals(java.util.Locale.ENGLISH, afterwards);
        } finally { worker.shutdownNow(); }
    }
    @Test void saturatedQueueRecordsFailureWithoutFailingCommittedRequest() {
        var meters = new SimpleMeterRegistry();
        var delivery = new NotificationDelivery(fake(true), new NotificationProperties(), meters,
                action -> { throw new java.util.concurrent.RejectedExecutionException(); });
        assertDoesNotThrow(() -> delivery.enqueue("PASSWORD_RESET", () -> fail("must not run")));
        assertEquals(1, meters.get("shopupu.notification.delivery").tag("outcome", "failed").counter().count());
    }

}
