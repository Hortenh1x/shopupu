package com.example.shopupu.notifications;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Side effects run AFTER the order transaction commits and on a separate pool,
 * so a broken mail provider can never fail a checkout or payment (NOTIF-03).
 */
@Component
@RequiredArgsConstructor
public class OrderNotificationListener {

    private final NotificationService notificationService;
    private final NotificationDelivery delivery;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        delivery.enqueue("ORDER_STATUS", () -> notificationService.sendOrderStatusUpdate(
                event.customerEmail(), event.orderNumber(), event.toStatus().name()));
    }
}
