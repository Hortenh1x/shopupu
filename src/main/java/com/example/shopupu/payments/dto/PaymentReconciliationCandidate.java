package com.example.shopupu.payments.dto;

import com.example.shopupu.payments.entity.PaymentStatus;
import java.math.BigDecimal;

/** Immutable database projection: no lazy entity reads during provider HTTP. */
public record PaymentReconciliationCandidate(Long paymentId, Long orderId, String provider,
        String externalId, BigDecimal amount, String currency, PaymentStatus status) {}
