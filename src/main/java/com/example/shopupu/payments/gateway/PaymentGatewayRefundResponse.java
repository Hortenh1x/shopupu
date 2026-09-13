package com.example.shopupu.payments.gateway;

public record PaymentGatewayRefundResponse(String externalRefundId, PaymentGatewayRefundStatus status) {}
