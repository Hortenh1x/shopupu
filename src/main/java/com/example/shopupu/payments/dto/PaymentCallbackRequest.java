package com.example.shopupu.payments.dto;

import com.example.shopupu.payments.entity.PaymentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * describes the PaymentCallbackRequest record.
 */
public record PaymentCallbackRequest(
        String externalEventId,

        @NotBlank
        String externalPaymentId,

        @NotNull
        PaymentStatus status,

        String details
) {
}