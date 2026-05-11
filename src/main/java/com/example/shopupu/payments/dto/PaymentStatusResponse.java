package com.example.shopupu.payments.dto;

import com.example.shopupu.payments.entity.PaymentStatus;

/**
 * describes the PaymentStatusResponse record.
 */
public record PaymentStatusResponse(
        Long paymentId,
        String externalPaymentId,
        PaymentStatus status
) {
}