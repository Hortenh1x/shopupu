package com.example.shopupu.payments.gateway;

import java.math.BigDecimal;

/**
 * describes the PaymentGatewayCreateRequest record.
 */
public record PaymentGatewayCreateRequest(
        Long orderId,
        Long paymentId,
        BigDecimal amount,
        String currency
) {
}