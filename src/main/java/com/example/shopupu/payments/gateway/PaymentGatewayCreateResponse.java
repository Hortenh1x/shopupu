package com.example.shopupu.payments.gateway;

import com.example.shopupu.payments.entity.PaymentStatus;

/**
 * describes the PaymentGatewayCreateResponse record.
 */
public record PaymentGatewayCreateResponse(
        String externalPaymentId,
        String provider,
        PaymentStatus status,
        String paymentUrl,
        String clientToken
) {
}