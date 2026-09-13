package com.example.shopupu.payments.dto;

import java.math.BigDecimal;

/** Immutable expected fields for an adapter's verified provider lookup; never exposes secrets. */
public record PaymentProviderSnapshot(String provider, Long orderId, Long paymentId, String externalPaymentId,
        BigDecimal amount, String currency, String refundOperationKey, String refundExternalId) {}
