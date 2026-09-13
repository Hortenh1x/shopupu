package com.example.shopupu.notifications;

import com.example.shopupu.common.i18n.SupportedLocales;
import com.example.shopupu.config.NotificationProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Locale;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/** Bounded best-effort retry on the notification pool. Acceptance does not prove inbox delivery. */
@Slf4j
@Component
public class NotificationDelivery {
    private final NotificationService sender;
    private final NotificationProperties properties;
    private final MeterRegistry meters;
    private final java.util.concurrent.Executor executor;
    public NotificationDelivery(NotificationService sender, NotificationProperties properties, MeterRegistry meters,
            @org.springframework.beans.factory.annotation.Qualifier("notificationExecutor") java.util.concurrent.Executor executor) {
        this.sender = sender; this.properties = properties; this.meters = meters; this.executor = executor;
    }
    public void enqueue(String kind, Runnable action) {
        if (!sender.isAvailable()) { record(kind, "unavailable"); return; }
        // AFTER_COMMIT listeners still run on the request thread: capture its language here, because the
        // worker thread has no request context and would otherwise render every email in English.
        Locale locale = SupportedLocales.current();
        try { executor.execute(() -> deliver(kind, () -> runWithLocale(locale, action))); }
        catch (java.util.concurrent.RejectedExecutionException ex) {
            record(kind, "failed");
            log.error("Email work rejected by bounded queue: kind={}", kind);
        }
    }
    public void deliver(String kind, Runnable action) {
        String deliveryId = UUID.randomUUID().toString();
        if (!sender.isAvailable()) { record(kind, "unavailable"); return; }
        for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
            try {
                action.run();
                record(kind, "accepted");
                log.info("Email accepted by provider: kind={} deliveryId={} attempt={}", kind, deliveryId, attempt);
                return;
            } catch (RuntimeException ex) {
                if (attempt == properties.getMaxAttempts()) break;
                record(kind, "retry");
                try { Thread.sleep(250L * attempt); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); break; }
            }
        }
        record(kind, "failed");
        // Provider exception messages may contain email addresses or request bodies: do not log them.
        log.error("Email delivery failed after bounded attempts: kind={} deliveryId={}", kind, deliveryId);
    }
    static void runWithLocale(Locale locale, Runnable action) {
        LocaleContextHolder.setLocale(locale);
        try { action.run(); } finally { LocaleContextHolder.resetLocaleContext(); }
    }
    private void record(String kind, String outcome) {
        meters.counter("shopupu.notification.delivery", "kind", kind, "outcome", outcome).increment();
    }
}
