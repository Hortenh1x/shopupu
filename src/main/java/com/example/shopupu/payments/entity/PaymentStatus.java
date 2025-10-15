package com.example.shopupu.payments.entity;

/**
 * RU: Возможные статусы платежа.
 * EN: Possible payment statuses.
 */
public enum PaymentStatus {
    CREATED,
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELED,
    REFUNDED;

    /**
     * RU: Конвертация статуса из Stripe в локальный enum.
     * EN: Converts Stripe status string to our enum.
     */
    public static PaymentStatus fromStripeStatus(String stripeStatus) {
        if (stripeStatus == null) return FAILED;

        return switch (stripeStatus) {
            case "requires_payment_method", "requires_confirmation", "processing" -> PENDING;
            case "succeeded" -> SUCCEEDED;
            case "canceled" -> CANCELED;
            default -> FAILED;
        };
    }
}