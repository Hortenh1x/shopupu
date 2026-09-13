package com.example.shopupu.payments.service;

import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.payments.dto.PaymentReconciliationCandidate;
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
 * Cross-checks local payment statuses against the configured provider.
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
    private final PaymentProperties paymentProperties;
    private long reconciliationCursor;

    @Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT3M")
    public synchronized void reconcilePayments() {
        Instant now = Instant.now();
        List<PaymentReconciliationCandidate> candidates = candidates(now, reconciliationCursor);
        if (candidates.isEmpty() && reconciliationCursor != 0) {
            reconciliationCursor = 0;
            candidates = candidates(now, 0);
        }
        if (candidates.isEmpty()) {
            return;
        }

        int checked = 0;
        int mismatches = 0;
        for (PaymentReconciliationCandidate payment : candidates) {
            reconciliationCursor = payment.paymentId();
            if (!paymentProperties.getDefaultProvider().equals(payment.provider())) {
                continue;
            }
            Optional<PaymentStatus> providerStatus;
            try {
                providerStatus = paymentGatewayClient.fetchPaymentStatus(
                    new com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest(payment.orderId(),
                            payment.paymentId(), payment.externalId(), payment.amount(), payment.currency()));
            } catch (Exception ex) {
                meterRegistry.counter("shopupu.payments", "result", "reconciliation_lookup_failed").increment();
                log.warn("Payment reconciliation lookup failed for payment {}", payment.paymentId());
                continue;
            }
            if (providerStatus.isEmpty()) {
                continue; // provider has no status API (stub) or the lookup failed — no information
            }
            checked++;
            if (isDiscrepancy(payment.status(), providerStatus.get())) {
                mismatches++;
                meterRegistry.counter("shopupu.payments", "result", "reconciliation_mismatch").increment();
                log.warn("Payment reconciliation mismatch: payment {} (order {}, external {}) is {} locally but {} at the provider",
                        payment.paymentId(), payment.orderId(), payment.externalId(),
                        payment.status(), providerStatus.get());
            }
        }
        log.info("Payment reconciliation: {} candidates, {} checked at the provider, {} mismatches",
                candidates.size(), checked, mismatches);
    }

    private List<PaymentReconciliationCandidate> candidates(Instant now, long afterId) {
        return paymentRepository.findReconciliationCandidates(paymentProperties.getDefaultProvider(), RECONCILABLE,
                now.minus(LOOKBACK_WINDOW), now.minus(GRACE_PERIOD), afterId,
                org.springframework.data.domain.PageRequest.of(0, 100));
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
