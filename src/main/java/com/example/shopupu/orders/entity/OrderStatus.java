package com.example.shopupu.orders.entity;

import java.util.Map;
import java.util.Set;

/**
 * Order lifecycle (ORD-01). Only the transitions listed here are legal:
 * CREATED -> PENDING_PAYMENT -> PAID -> PROCESSING -> SHIPPED -> DELIVERED -> COMPLETED
 * with CANCELLED before payment and REFUNDED after it.
 */
public enum OrderStatus {
    CREATED,
    PENDING_PAYMENT,
    PAID,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    COMPLETED,
    CANCELLED,
    REFUNDED;

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            CREATED, Set.of(PENDING_PAYMENT, PAID, CANCELLED),
            PENDING_PAYMENT, Set.of(PAID, CREATED, CANCELLED),
            PAID, Set.of(PROCESSING, SHIPPED, REFUNDED),
            PROCESSING, Set.of(SHIPPED, REFUNDED),
            SHIPPED, Set.of(DELIVERED, COMPLETED),
            DELIVERED, Set.of(COMPLETED, REFUNDED),
            COMPLETED, Set.of(REFUNDED),
            CANCELLED, Set.of(),
            REFUNDED, Set.of()
    );

    public boolean canTransitionTo(OrderStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
