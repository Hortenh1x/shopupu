package com.example.shopupu.payments.dto;

import com.example.shopupu.payments.entity.PaymentStatus;

public record PaymentEventDto(
        String externalPaymentId,
        PaymentStatus status
) {}
