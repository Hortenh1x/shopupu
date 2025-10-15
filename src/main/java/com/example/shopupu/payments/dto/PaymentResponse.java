package com.example.shopupu.payments.dto;

import com.example.shopupu.payments.entity.PaymentStatus;

import java.math.BigDecimal;

/**
 * RU: Ответ от платёжного провайдера.
 * EN: Response from payment provider.
 */
public record PaymentResponse(
        String externalPaymentId,
        String provider,
        PaymentStatus status,
        BigDecimal amount,
        String clientSecret
) {}
