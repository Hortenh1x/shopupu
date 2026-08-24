package com.example.shopupu.payments.service;

import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.gateway.PaymentGatewayClient;
import com.example.shopupu.payments.repository.PaymentRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cross-checks local payment statuses against the provider (monobank/Fondy).
 * The signed webhook stays the single source of truth (ADR-0003) — this job only
 * detects drift (e.g. a lost webhook after the customer paid, or a payment we
 * expired that actually succeeded) and reports it via WARN logs and the
 * shopupu.payments metric (result=reconciliation_mismatch) for the runbook.
 *
 * ADR-0003 shape: candidates are loaded first, then the provider HTTP calls run
 * without any DB transaction or connection held; the job writes nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationJob {

    /** Local states worth re-checking: still in flight, or expired by our own TTL job. */
    private static final EnumSet<PaymentStatus> RECONCILABLE =
            EnumSet.of(PaymentStatus.CREATED, PaymentStatus.PENDING, PaymentStatus.EXPIRED);

    /** Not yet settled on either side — CREATED vs PENDING is normal in-flight skew, not drift. */
    private static final EnumSet<PaymentStatus> IN_FLIGHT =
            EnumSet.of(PaymentStatus.CREATED, PaymentStatus.PENDING);

    /** Terminal without money moved — FAILED vs EXPIRED vs CANCELED differences change nothing. */
    private static final EnumSet<PaymentStatus> TERMINAL_NO_MONEY =
            EnumSet.of(PaymentStatus.FAILED, PaymentStatus.CANCELED, PaymentStatus.EXPIRED);

    /** Give the webhook a head start before asking the provider ourselves. */
    private static final Duration GRACE_PERIOD = Duration.ofMinutes(5);

    /** How far back to look; older drift is a runbook/archaeology case, not a monitoring one. */
    private static final Duration LOOKBACK_WINDOW = Duration.ofHours(24);

    private final PaymentRepository paymentRepository;
    private final PaymentGatewayClient paymentGatewayClient;
    private final MeterRegistry meterRegistry;

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT3M")
    public void reconcilePayments() {
        Instant now = Instant.now();
        List<Payment> candidates = paymentRepository.findTop100ByStatusInAndExternalIdIsNotNullAndCreatedAtBetween(
                RECONCILABLE, now.minus(LOOKBACK_WINDOW), now.minus(GRACE_PERIOD));
        if (candidates.isEmpty()) {
            return;
        }

        int checked = 0;
        int mismatches = 0;
        for (Payment payment : candidates) {
            Optional<PaymentStatus> providerStatus = paymentGatewayClient.fetchPaymentStatus(payment.getExternalId());
            if (providerStatus.isEmpty()) {
                continue; // provider has no status API (stub) or the lookup failed — no information
            }
            checked++;
            if (isDiscrepancy(payment.getStatus(), providerStatus.get())) {
                mismatches++;
                meterRegistry.counter("shopupu.payments", "result", "reconciliation_mismatch").increment();
                log.warn("Payment reconciliation mismatch: payment {} (order {}, external {}) is {} locally but {} at the provider",
                        payment.getId(), payment.getOrder().getId(), payment.getExternalId(),
                        payment.getStatus(), providerStatus.get());
            }
        }
        log.info("Payment reconciliation: {} candidates, {} checked at the provider, {} mismatches",
                candidates.size(), checked, mismatches);
    }

    static boolean isDiscrepancy(PaymentStatus local, PaymentStatus provider) {
        if (local == provider) {
            return false;
        }
        if (IN_FLIGHT.contains(local) && IN_FLIGHT.contains(provider)) {
            return false;
        }
        return !(TERMINAL_NO_MONEY.contains(local) && TERMINAL_NO_MONEY.contains(provider));
    }
}
