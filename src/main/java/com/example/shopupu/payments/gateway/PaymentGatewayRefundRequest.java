package com.example.shopupu.payments.gateway;

public record PaymentGatewayRefundRequest(Long orderId, Long paymentId, String externalPaymentId, java.math.BigDecimal amount, String currency, String idempotencyKey) {}
