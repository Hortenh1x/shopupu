package com.example.shopupu.payments.entity;

import java.util.Map;
import java.util.Set;

public enum PaymentStatus {
    CREATED,
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    EXPIRED,
    REFUNDED;

    private static final Map<PaymentStatus, Set<PaymentStatus>> ALLOWED = Map.of(
            CREATED, Set.of(PENDING, SUCCEEDED, FAILED, CANCELED, EXPIRED),
            PENDING, Set.of(SUCCEEDED, FAILED, CANCELED, EXPIRED),
            SUCCEEDED, Set.of(REFUNDED),
            FAILED, Set.of(),
            CANCELED, Set.of(),
            EXPIRED, Set.of(),
            REFUNDED, Set.of()
    );

    /** Guards webhook processing: a SUCCEEDED payment can never become FAILED (PAY-04). */
    public boolean canTransitionTo(PaymentStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
