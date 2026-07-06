package com.example.shopupu.notifications;

import com.example.shopupu.orders.entity.OrderStatus;

/** Domain event published on every order status transition (ASYNC-02). */
public record OrderStatusChangedEvent(
        Long orderId,
        String orderNumber,
        String customerEmail,
        OrderStatus fromStatus,
        OrderStatus toStatus
) {
}
