package com.example.shopupu.payments.gateway;

import java.math.BigDecimal;

/**
 * describes the PaymentGatewayCreateRequest record.
 */
public record PaymentGatewayCreateRequest(
        Long orderId,
        Long paymentId,
        BigDecimal amount,
        String currency,
        String idempotencyKey,
        String providerRequestContext
) {
    public PaymentGatewayCreateRequest(Long orderId, Long paymentId, BigDecimal amount, String currency, String idempotencyKey) {
        this(orderId, paymentId, amount, currency, idempotencyKey, null);
    }

    public PaymentGatewayCreateRequest(Long orderId, Long paymentId, BigDecimal amount, String currency) {
        this(orderId, paymentId, amount, currency, "payment-" + paymentId + "-" + orderId, null);
    }
}
