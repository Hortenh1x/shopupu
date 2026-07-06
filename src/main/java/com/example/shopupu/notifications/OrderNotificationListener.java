package com.example.shopupu.notifications;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Side effects run AFTER the order transaction commits and on a separate pool,
 * so a broken mail provider can never fail a checkout or payment (NOTIF-03).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final NotificationService notificationService;

    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        try {
            notificationService.sendOrderStatusUpdate(
                    event.customerEmail(), event.orderNumber(), event.toStatus().name());
        } catch (Exception ex) {
            // never propagate: notifications are best-effort side effects
            log.warn("Failed to send order notification for {}", event.orderNumber(), ex);
        }
    }
}
