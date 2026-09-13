package com.example.shopupu.payments.gateway;

public record PaymentGatewayStatusRequest(Long orderId, Long paymentId, String externalPaymentId, java.math.BigDecimal amount, String currency) {}
