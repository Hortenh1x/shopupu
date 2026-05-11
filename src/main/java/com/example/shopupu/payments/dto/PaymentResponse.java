package com.example.shopupu.payments.dto;

import com.example.shopupu.payments.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;


/**
 * describes the PaymentResponse record.
 */
public record PaymentResponse(
        Long id,
        Long orderId,
        String externalPaymentId,
        String provider,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String paymentUrl,
        String clientToken,
        Instant createdAt,
        Instant updatedAt
) {}