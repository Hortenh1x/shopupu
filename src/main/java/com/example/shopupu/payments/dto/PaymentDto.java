package com.example.shopupu.payments.dto;

import com.example.shopupu.payments.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentDto(
        Long id,
        Long orderId,
        BigDecimal amount,
        String currency,
        String provider,
        PaymentStatus status,
        String providerPaymentId,
        String clientSecret,
        Instant createdAt,
        Instant updatedAt
) {}
