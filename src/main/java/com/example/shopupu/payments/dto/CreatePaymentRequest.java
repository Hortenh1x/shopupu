package com.example.shopupu.payments.dto;

import jakarta.validation.constraints.NotNull;

/**
 * describes the CreatePaymentRequest record.
 */
public record CreatePaymentRequest(
        @NotNull
        Long orderId
) {
}