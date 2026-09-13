package com.example.shopupu.payments.service;

import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ConflictException;
import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.exception.ServiceUnavailableException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.config.PaymentProperties;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.orders.service.OrderService;
import com.example.shopupu.payments.dto.PaymentCallbackRequest;
import com.example.shopupu.payments.dto.PaymentResponse;
import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentEvent;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.gateway.*;
import com.example.shopupu.payments.mapper.PaymentMapper;
import com.example.shopupu.payments.repository.PaymentEventRepository;
import com.example.shopupu.payments.repository.PaymentRepository;
import com.example.shopupu.shipping.entity.ShippingAddress;
import com.example.shopupu.shipping.entity.ShippingMethod;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final PaymentEventRepository paymentEventRepository;
    private final OrderRepository orderRepository;
    private final PaymentMapper paymentMapper;
    private final PaymentGatewayClient paymentGatewayClient;
    private final PaymentCallbackVerifier paymentCallbackVerifier;
    private final PaymentProperties paymentProperties;
    private final ShipmentRepository shipmentRepository;
    private final OrderService orderService;
    private final AccessControlService accessControlService;
    private final TransactionTemplate transactionTemplate;
    private final com.example.shopupu.common.audit.AuditService auditService;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
    private final com.example.shopupu.payments.repository.PaymentRefundAttemptRepository refundAttemptRepository;

    // Access is serialized by reconcileRefunds; unresolved rows cannot monopolize every batch.
    private long refundReconciliationCursor;
    private long createReconciliationCursor;

    @Transactional(propagation = Propagation.NEVER)
    public PaymentResponse createPayment(Long orderId) {
        return createPayment(orderId, null);
    }

    /** Commit local identity and freeze checkout before HTTP; an unknown result never authorizes another attempt. */
    @Transactional(propagation = Propagation.NEVER)
    public PaymentResponse createPayment(Long orderId, String clientIdempotencyKey) {
        String key = hasText(clientIdempotencyKey) ? clientIdempotencyKey : UUID.randomUUID().toString();
        if (key.length() > 64) throw new BusinessRuleException("Idempotency-Key must contain at most 64 characters");
        PreparedPayment prepared = transactionTemplate.execute(tx -> preparePayment(orderId, key));
        if (prepared.replay() != null) return prepared.replay();

        PaymentGatewayCreateResponse response;
        try {
            response = paymentGatewayClient.createPayment(prepared.request());
        } catch (PaymentGatewayCreateException ex) {
            transactionTemplate.executeWithoutResult(tx -> markDefinitiveCreateFailure(prepared.request().paymentId()));
            throw new BusinessRuleException("Payment provider rejected this attempt; a new payment may be started");
        } catch (Exception ex) {
            log.warn("Payment create outcome unknown for payment {}", prepared.request().paymentId());
            transactionTemplate.executeWithoutResult(tx -> {
                Payment payment = lockPayment(prepared.request().paymentId());
                recordEvent(payment, null, payment.getStatus(), "SYSTEM", "Provider create outcome unknown; reconciliation required");
            });
            meterRegistry.counter("shopupu.payments", "result", "create_unknown").increment();
            throw new ServiceUnavailableException("PAYMENT_OUTCOME_UNKNOWN", "Payment outcome is unknown; check this payment before retrying");
        }
        return transactionTemplate.execute(tx -> applyGatewayResult(prepared.request().paymentId(), response));
    }

    private PreparedPayment preparePayment(Long orderId, String key) {
        // Transaction-scoped PostgreSQL lock also covers first insertion and keys used against different orders.
        paymentRepository.lockIdempotencyKey(key);
        var existing = paymentRepository.findByIdempotencyKey(key);
        if (existing.isPresent()) {
            Payment payment = existing.get();
            Order originalOrder = lockOrder(payment.getOrder().getId());
            accessControlService.requireOrderOwnerOrAdmin(originalOrder);
            requireActiveOrderOwner(originalOrder.getId());
            if (!payment.getOrder().getId().equals(orderId)) {
                throw new ConflictException("Idempotency-Key belongs to a different order");
            }
            return new PreparedPayment(null, paymentMapper.toResponse(payment));
        }
        Order order = lockOrder(orderId);
        accessControlService.requireOrderOwnerOrAdmin(order);
        requireActiveOrderOwner(order.getId());
        validateOrderCanBePaid(order);
        if (orderRepository.hasUnsettledPayment(orderId)) {
            throw new BusinessRuleException("Payment is already in progress");
        }
        // Local configuration validation only; this hook must never perform provider HTTP.
        paymentGatewayClient.ensureAvailable();
        Payment payment = paymentRepository.save(Payment.builder().order(order)
                .amount(order.getPaymentAmount()).provider(paymentProperties.getDefaultProvider())
                .status(PaymentStatus.CREATED).idempotencyKey(key).currency(paymentProperties.getCurrency()).build());
        payment.setProviderRequestContext(paymentGatewayClient.snapshotCreateContext(createRequest(payment)));
        paymentRepository.saveAndFlush(payment);
        orderService.markPendingPayment(orderId);
        recordEvent(payment, null, PaymentStatus.CREATED, "SYSTEM", "Payment prepared");
        return new PreparedPayment(createRequest(payment), null);
    }

    private void requireActiveOrderOwner(Long orderId) {
        // The order lock serializes erasure; do not invert user->order by locking the user here.
        if (!paymentRepository.isOrderOwnerActive(orderId)) {
            throw new ForbiddenOperationException("Account is unavailable");
        }
    }

    private void markDefinitiveCreateFailure(Long id) {
        Payment payment = lockPayment(id);
        // A completed callback is more authoritative than a late create response.
        if (payment.getStatus() == PaymentStatus.CREATED && !hasText(payment.getExternalId())) {
            applyTransition(payment, PaymentStatus.FAILED, null, "SYSTEM", "Provider definitively rejected create");
        }
    }

    /** Bounded recovery of the original provider operation; expired retry windows require manual provider lookup. */
    @Transactional(propagation = Propagation.NEVER)
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "PT1M", initialDelayString = "PT1M")
    public synchronized void reconcileCreates() {
        Instant now = Instant.now();
        var candidates = createCandidates(createReconciliationCursor, now.minusSeconds(60));
        if (candidates.isEmpty() && createReconciliationCursor != 0) {
            createReconciliationCursor = 0;
            candidates = createCandidates(0, now.minusSeconds(60));
        }
        for (CreateCandidate candidate : candidates) {
            createReconciliationCursor = candidate.request().paymentId();
            Instant retryUntil = candidate.preparedAt().plusSeconds(23 * 3600);
            if (!now.isBefore(retryUntil)) {
                meterRegistry.counter("shopupu.payments", "result", "create_manual_reconciliation").increment();
                log.warn("Create recovery window elapsed for payment {}; manual provider lookup required", candidate.request().paymentId());
                continue;
            }
            try {
                paymentGatewayClient.recoverPayment(candidate.request(), candidate.preparedAt(), retryUntil)
                        .ifPresent(result -> transactionTemplate.executeWithoutResult(tx ->
                                applyGatewayResult(candidate.request().paymentId(), result)));
            } catch (PaymentGatewayCreateException ex) {
                transactionTemplate.executeWithoutResult(tx -> markDefinitiveCreateFailure(candidate.request().paymentId()));
            } catch (Exception ex) {
                log.warn("Create outcome still unknown for payment {}", candidate.request().paymentId());
            }
        }
    }

    private java.util.List<CreateCandidate> createCandidates(long afterId, Instant before) {
        return transactionTemplate.execute(tx -> paymentRepository.findCreateRecoveryCandidates(
                paymentProperties.getDefaultProvider(), before, afterId, org.springframework.data.domain.PageRequest.of(0, 100))
                .stream().map(p -> new CreateCandidate(createRequest(p), p.getCreatedAt())).toList());
    }

    private PaymentGatewayCreateRequest createRequest(Payment payment) {
        return new PaymentGatewayCreateRequest(payment.getOrder().getId(), payment.getId(), payment.getAmount(),
                payment.getCurrency(), payment.getIdempotencyKey(), payment.getProviderRequestContext());
    }

    private PaymentResponse applyGatewayResult(Long paymentId, PaymentGatewayCreateResponse response) {
        Payment payment = lockPayment(paymentId);
        if (response == null || !hasText(response.externalPaymentId())
                || !payment.getProvider().equals(response.provider())) {
            throw new ServiceUnavailableException("PAYMENT_OUTCOME_UNKNOWN", "Payment provider returned inconsistent details; reconciliation required");
        }
        bindExternalId(payment, response.externalPaymentId());
        boolean ownerActive = paymentRepository.isOrderOwnerActive(payment.getOrder().getId());
        payment.setPaymentUrl(ownerActive ? response.paymentUrl() : null);
        payment.setClientToken(ownerActive ? response.clientToken() : null);
        // A fast signed callback may have already completed the payment. The create response cannot regress it.
        if (payment.getStatus() == PaymentStatus.CREATED && response.status() == PaymentStatus.PENDING) {
            payment.setStatus(PaymentStatus.PENDING);
        }
        paymentRepository.saveAndFlush(payment);
        recordEvent(payment, null, payment.getStatus(), "SYSTEM", "Payment registered at provider");
        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public void handleCallback(PaymentCallbackRequest callback, String rawPayload, String signature) {
        if ("stripe".equals(paymentProperties.getDefaultProvider())) {
            throw new ForbiddenOperationException("Stripe callbacks require native Stripe signature verification");
        }
        if (!paymentCallbackVerifier.isValid(rawPayload, signature)) {
            throw new ForbiddenOperationException("Invalid payment callback signature");
        }
        applyVerifiedCallback(paymentProperties.getDefaultProvider(), callback);
    }

    /**
     * Provider identity comes from the verified adapter, never from webhook JSON.
     * The adapter must first verify its native signature and the expected payment fields.
     */
    @Transactional
    public void applyVerifiedCallback(String verifiedProvider, PaymentCallbackRequest callback) {
        if (callback.status() == null || !hasText(callback.externalPaymentId())) {
            throw new BusinessRuleException("Payment callback is incomplete");
        }
        Long id = callback.localPaymentId() != null ? callback.localPaymentId()
                : paymentRepository.findIdByExternalId(callback.externalPaymentId())
                        .orElseThrow(() -> new ResourceNotFoundException("Payment not found for callback"));
        Payment payment = lockPayment(id);
        if (!hasText(verifiedProvider) || !verifiedProvider.equals(payment.getProvider())) {
            throw new ForbiddenOperationException("Payment callback provider does not match this payment");
        }
        bindExternalId(payment, callback.externalPaymentId());
        applyTransition(payment, callback.status(), callback.externalEventId(), "PAYMENT_CALLBACK", callback.details());
    }

    @Transactional
    public void handleCallback(PaymentCallbackRequest callback, String signature) {
        handleCallback(callback, "", signature);
    }

    /** Local demo completion, only for this payment's owner and the stub provider. Controller must gate demo mode. */
    @Transactional
    public PaymentResponse simulateSuccess(Long paymentId) {
        Payment payment = lockPayment(paymentId);
        if (!payment.getOrder().getUser().getId().equals(accessControlService.currentUser().getId())) {
            throw new ForbiddenOperationException("Only the payment owner can simulate a payment");
        }
        requireActiveOrderOwner(payment.getOrder().getId());
        if (!"stub".equals(payment.getProvider()) || !"stub".equals(paymentProperties.getDefaultProvider())) {
            throw new BusinessRuleException("Only local stub payments can be simulated");
        }
        applyTransition(payment, PaymentStatus.SUCCEEDED, "stub-success-" + paymentId, "DEMO", "Local demo payment");
        paymentRepository.flush();
        return paymentMapper.toResponse(payment);
    }

    /** Real provider timeouts are uncertainty, not proof of expiry. Stub sessions have no external money movement. */
    @Transactional(propagation = Propagation.NEVER)
    public int expireStalePayments(Instant cutoff) {
        int expired = 0;
        for (Long id : paymentRepository.findStaleUnfinishedIds(cutoff)) {
            Boolean changed = transactionTemplate.execute(tx -> {
                Payment payment = lockPayment(id);
                if (payment.getStatus() != PaymentStatus.CREATED && payment.getStatus() != PaymentStatus.PENDING) return false;
                if (!"stub".equals(payment.getProvider())) {
                    meterRegistry.counter("shopupu.payments", "result", "expiry_unresolved").increment();
                    log.warn("Provider payment {} is overdue; reservation retained until confirmed resolution", id);
                    return false;
                }
                applyTransition(payment, PaymentStatus.EXPIRED, null, "SYSTEM", "Local stub session expired");
                return true;
            });
            if (Boolean.TRUE.equals(changed)) expired++;
        }
        return expired;
    }

    /** Persist one refund operation before HTTP. Ordinary repeats never start another refund. */
    @Transactional(propagation = Propagation.NEVER)
    public PaymentResponse refundPayment(Long paymentId) {
        return executeRefund(paymentId, null);
    }

    /** Explicit compare-and-set retry of a definitively failed operation. Replayed retries keep the new operation. */
    @Transactional(propagation = Propagation.NEVER)
    public PaymentResponse retryRefundPayment(Long paymentId, String failedOperationKey) {
        if (!hasText(failedOperationKey)) throw new BusinessRuleException("Failed refund operation key is required");
        return executeRefund(paymentId, failedOperationKey);
    }

    private PaymentResponse executeRefund(Long paymentId, String failedOperationKey) {
        accessControlService.requireAdmin();
        String actor = accessControlService.currentEmail();
        PreparedRefund prepared = transactionTemplate.execute(tx -> prepareRefund(paymentId, failedOperationKey));
        if (prepared.replay() != null) return prepared.replay();
        String operationKey = prepared.request().idempotencyKey();
        PaymentGatewayRefundResponse response;
        try {
            response = paymentGatewayClient.refundPayment(prepared.request());
        } catch (UnsupportedOperationException ex) {
            applyRefundOutcome(paymentId, operationKey, new PaymentGatewayRefundResponse(null, PaymentGatewayRefundStatus.FAILED), actor);
            throw new BusinessRuleException("Refunds are not supported by this payment provider");
        } catch (Exception ex) {
            log.warn("Refund outcome unknown for payment {}", paymentId);
            applyRefundOutcome(paymentId, operationKey, new PaymentGatewayRefundResponse(null, PaymentGatewayRefundStatus.UNKNOWN), actor);
            meterRegistry.counter("shopupu.payments", "result", "refund_unknown").increment();
            throw new ServiceUnavailableException("REFUND_OUTCOME_UNKNOWN", "Refund outcome is unknown; reconcile this operation before retrying");
        }
        PaymentResponse applied = applyRefundOutcome(paymentId, operationKey, response, actor);
        if (applied.refundStatus() == PaymentGatewayRefundStatus.UNKNOWN) {
            throw new ServiceUnavailableException("REFUND_OUTCOME_UNKNOWN", "Refund outcome is unknown; reconcile this operation before retrying");
        }
        return applied;
    }

    private PreparedRefund prepareRefund(Long paymentId, String failedOperationKey) {
        Payment payment = lockPayment(paymentId);
        if (payment.getStatus() == PaymentStatus.REFUNDED) return new PreparedRefund(null, paymentMapper.toResponse(payment));
        if (hasText(payment.getRefundOperationKey())) {
            if (failedOperationKey == null || !failedOperationKey.equals(payment.getRefundOperationKey())) {
                return new PreparedRefund(null, paymentMapper.toResponse(payment));
            }
            if (payment.getRefundStatus() != PaymentGatewayRefundStatus.FAILED
                    && payment.getRefundStatus() != PaymentGatewayRefundStatus.CANCELED) {
                throw new BusinessRuleException("Only definitively failed or canceled refunds can be retried");
            }
            saveRefundAttempt(payment);
        } else if (failedOperationKey != null) {
            throw new BusinessRuleException("There is no failed refund operation to retry");
        }
        if (payment.getStatus() != PaymentStatus.SUCCEEDED
                || !payment.getOrder().getStatus().canTransitionTo(OrderStatus.REFUNDED)) {
            throw new BusinessRuleException("Only a paid order eligible for refund can be refunded");
        }
        if (!payment.getProvider().equals(paymentProperties.getDefaultProvider()) || !hasText(payment.getExternalId())) {
            throw new BusinessRuleException("Payment provider is unavailable for this refund");
        }
        paymentGatewayClient.ensureAvailable();
        payment.setRefundOperationKey(UUID.randomUUID().toString());
        payment.setRefundStatus(PaymentGatewayRefundStatus.PENDING);
        payment.setRefundExternalId(null);
        paymentRepository.saveAndFlush(payment);
        saveRefundAttempt(payment);
        recordEvent(payment, null, payment.getStatus(), "ADMIN", "Refund requested: " + payment.getRefundOperationKey());
        return new PreparedRefund(refundRequest(payment), null);
    }

    private PaymentResponse applyRefundOutcome(Long id, String operationKey, PaymentGatewayRefundResponse response, String actor) {
        return transactionTemplate.execute(tx -> applyRefundOutcomeLocked(lockPayment(id), operationKey, response, actor, null));
    }

    /** Native adapter verifies signature, live/test mode and full amount/currency/metadata before calling. */
    @Transactional
    public PaymentResponse applyVerifiedRefundCallback(String provider, Long paymentId, String operationKey,
            String eventId, PaymentGatewayRefundResponse response) {
        Payment payment = lockPayment(paymentId);
        if (!hasText(provider) || !provider.equals(payment.getProvider())) {
            throw new ForbiddenOperationException("Refund callback provider does not match this payment");
        }
        if (!hasText(operationKey)) throw new ForbiddenOperationException("Refund operation key is required");
        if (!operationKey.equals(payment.getRefundOperationKey())) {
            var previous = refundAttemptRepository.findById(operationKey);
            if (previous.isEmpty() || !previous.get().getPayment().getId().equals(paymentId)
                    || !provider.equals(previous.get().getProvider())) {
                throw new ForbiddenOperationException("Refund operation does not belong to this payment");
            }
        }
        return applyRefundOutcomeLocked(payment, operationKey, response, "payment-callback", eventId);
    }

    private PaymentResponse applyRefundOutcomeLocked(Payment payment, String operationKey,
            PaymentGatewayRefundResponse response, String actor, String eventId) {
        PaymentGatewayRefundStatus outcome = response == null || response.status() == null
                ? PaymentGatewayRefundStatus.UNKNOWN : response.status();
        String details = "Refund " + operationKey + " outcome: " + outcome;
        boolean currentOperation = operationKey != null && operationKey.equals(payment.getRefundOperationKey());
        if (hasText(eventId) && paymentEventRepository.insertCallbackEvent(payment.getId(), eventId,
                currentOperation && outcome == PaymentGatewayRefundStatus.SUCCEEDED ? PaymentStatus.REFUNDED.name() : payment.getStatus().name(),
                "REFUND_CALLBACK", currentOperation ? details : "Ignored stale " + details) == 0) {
            return paymentMapper.toResponse(payment);
        }
        if (!currentOperation) {
            if (!hasText(eventId)) recordEvent(payment, null, payment.getStatus(), "REFUND", "Ignored stale " + details);
            return paymentMapper.toResponse(payment);
        }
        if (payment.getStatus() == PaymentStatus.REFUNDED) return paymentMapper.toResponse(payment);
        // A late accepted/unknown response cannot undo a terminal verified failure for the same operation.
        if ((payment.getRefundStatus() == PaymentGatewayRefundStatus.FAILED || payment.getRefundStatus() == PaymentGatewayRefundStatus.CANCELED)
                && (outcome == PaymentGatewayRefundStatus.PENDING || outcome == PaymentGatewayRefundStatus.UNKNOWN)) {
            recordEvent(payment, null, payment.getStatus(), "REFUND", "Ignored outdated " + details);
            return paymentMapper.toResponse(payment);
        }
        if (response != null && hasText(response.externalRefundId())) {
            if (hasText(payment.getRefundExternalId()) && !payment.getRefundExternalId().equals(response.externalRefundId())) {
                throw new BusinessRuleException("Refund provider identity does not match the prepared operation");
            }
            payment.setRefundExternalId(response.externalRefundId());
        }
        payment.setRefundStatus(outcome);
        if (outcome == PaymentGatewayRefundStatus.SUCCEEDED) {
            applyTransition(payment, PaymentStatus.REFUNDED, null, "REFUND", details, actor);
            auditService.record(actor, "PAYMENT_REFUNDED", "payment", String.valueOf(payment.getId()), "amount=" + payment.getAmount());
        } else {
            recordEvent(payment, null, payment.getStatus(), "REFUND", details);
        }
        saveRefundAttempt(payment);
        paymentRepository.saveAndFlush(payment);
        return paymentMapper.toResponse(payment);
    }

    private void saveRefundAttempt(Payment payment) {
        var attempt = refundAttemptRepository.findById(payment.getRefundOperationKey()).orElseGet(() ->
                com.example.shopupu.payments.entity.PaymentRefundAttempt.builder()
                        .operationKey(payment.getRefundOperationKey()).payment(payment).provider(payment.getProvider()).build());
        attempt.setStatus(payment.getRefundStatus());
        attempt.setExternalRefundId(payment.getRefundExternalId());
        refundAttemptRepository.save(attempt);
    }

    /** Read-only provider recovery; pending/unknown attempts are never automatically submitted again. */
    @Transactional(propagation = Propagation.NEVER)
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT3M")
    public synchronized void reconcileRefunds() {
        String provider = paymentProperties.getDefaultProvider();
        var candidates = refundCandidates(provider, refundReconciliationCursor);
        if (candidates.isEmpty() && refundReconciliationCursor != 0) {
            refundReconciliationCursor = 0;
            candidates = refundCandidates(provider, 0);
        }
        for (RefundCandidate candidate : candidates) {
            // Advance even when the provider has no answer, so later operations get their turn.
            refundReconciliationCursor = candidate.request().paymentId();
            try {
                paymentGatewayClient.fetchRefundStatus(candidate.request(), candidate.externalRefundId())
                        .ifPresent(result -> applyRefundOutcome(candidate.request().paymentId(), candidate.request().idempotencyKey(), result, "system:reconciliation"));
            } catch (Exception ex) {
                log.warn("Refund reconciliation unavailable for payment {}", candidate.request().paymentId());
            }
        }
    }

    private java.util.List<RefundCandidate> refundCandidates(String provider, long afterId) {
        return transactionTemplate.execute(tx -> paymentRepository.findRefundCandidates(provider,
                EnumSet.of(PaymentGatewayRefundStatus.PENDING, PaymentGatewayRefundStatus.UNKNOWN), afterId,
                org.springframework.data.domain.PageRequest.of(0, 100)).stream()
                .map(p -> new RefundCandidate(refundRequest(p), p.getRefundExternalId())).toList());
    }

    private void applyTransition(Payment payment, PaymentStatus next, String eventId, String source, String details) {
        applyTransition(payment, next, eventId, source, details, source);
    }

    private void applyTransition(Payment payment, PaymentStatus next, String eventId, String source, String details, String actor) {
        PaymentStatus current = payment.getStatus();
        boolean duplicateStatus = current == next;
        // A verified full refund also proves the payment succeeded. Provider events can arrive out of order.
        boolean refundBeforeSuccess = next == PaymentStatus.REFUNDED
                && (current == PaymentStatus.CREATED || current == PaymentStatus.PENDING);
        boolean legal = duplicateStatus || refundBeforeSuccess || current.canTransitionTo(next);
        String eventDetails = duplicateStatus ? "Duplicate status: " + next : legal ? details
                : "Rejected illegal transition " + current + " -> " + next;
        // INSERT ... ON CONFLICT also deduplicates the same provider event racing across different payment IDs.
        if (paymentEventRepository.insertCallbackEvent(payment.getId(), hasText(eventId) ? eventId : null,
                (legal ? next : current).name(), source, eventDetails) == 0) return;
        if (!legal || duplicateStatus) return;

        Long orderId = payment.getOrder().getId();
        if (next == PaymentStatus.SUCCEEDED) {
            orderService.markPaidFromPayment(orderId);
            meterRegistry.counter("shopupu.payments", "result", "succeeded").increment();
        } else if (next == PaymentStatus.REFUNDED) {
            if (refundBeforeSuccess) orderService.markPaidFromPayment(orderId);
            orderService.markRefunded(orderId, actor);
            payment.setRefundStatus(PaymentGatewayRefundStatus.SUCCEEDED);
            if (hasText(payment.getRefundOperationKey())) saveRefundAttempt(payment);
        } else if (next == PaymentStatus.FAILED || next == PaymentStatus.CANCELED || next == PaymentStatus.EXPIRED) {
            orderService.onPaymentFailed(orderId);
            meterRegistry.counter("shopupu.payments", "result", "failed").increment();
        }
        payment.setStatus(next);
        paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForCurrentUser(Long id) {
        Payment payment = paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        accessControlService.requireOrderOwnerOrAdmin(payment.getOrder());
        return paymentMapper.toResponse(payment);
    }

    @Transactional(readOnly = true)
    public com.example.shopupu.payments.dto.PaymentProviderSnapshot getProviderSnapshot(Long id) {
        Payment payment = paymentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        return new com.example.shopupu.payments.dto.PaymentProviderSnapshot(payment.getProvider(), payment.getOrder().getId(),
                payment.getId(), payment.getExternalId(), payment.getAmount(), payment.getCurrency(),
                payment.getRefundOperationKey(), payment.getRefundExternalId());
    }

    /** Adapter has fetched and validated this exact provider object outside a DB transaction. */
    @Transactional
    public PaymentResponse applyVerifiedSessionRecovery(Long id, PaymentGatewayCreateResponse response) {
        accessControlService.requireAdmin();
        Payment payment = lockPayment(id);
        if (!"stripe".equals(payment.getProvider()) || !"stripe".equals(response.provider())) {
            throw new ForbiddenOperationException("Wrong payment provider");
        }
        bindExternalId(payment, response.externalPaymentId());
        if (response.paymentUrl() != null && paymentRepository.isOrderOwnerActive(payment.getOrder().getId())) {
            if (!com.example.shopupu.payments.gateway.StripePaymentGatewayClient.isHostedTestUrl(response.paymentUrl())) {
                throw new BusinessRuleException("Invalid hosted test checkout URL");
            }
            payment.setPaymentUrl(response.paymentUrl());
        }
        applyTransition(payment, response.status(), "recovery:" + response.externalPaymentId() + ":" + response.status(),
                "ADMIN_RECOVERY", "Verified operator recovery from Stripe test session", accessControlService.currentEmail());
        paymentRepository.flush();
        return paymentMapper.toResponse(payment);
    }

    private Payment lockPayment(Long id) {
        Long orderId = paymentRepository.findOrderIdByPaymentId(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        lockOrder(orderId);
        return paymentRepository.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
    }

    private Order lockOrder(Long id) {
        return orderRepository.findLockedById(id).orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private void bindExternalId(Payment payment, String externalId) {
        if (hasText(payment.getExternalId()) && !payment.getExternalId().equals(externalId)) {
            throw new BusinessRuleException("Provider payment identity does not match the prepared payment");
        }
        payment.setExternalId(externalId);
    }

    private void validateOrderCanBePaid(Order order) {
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Only unpaid orders can be paid");
        }
        var shipment = shipmentRepository.findByOrder(order)
                .orElseThrow(() -> new BusinessRuleException("Shipping must be selected before payment"));
        if (shipment.getMethod() == null) throw new BusinessRuleException("Shipping method must be selected before payment");
        if (shipment.getMethod() != ShippingMethod.LOCAL_PICKUP) {
            ShippingAddress address = shipment.getAddress();
            if (address == null || !hasText(address.getFullName()) || !hasText(address.getLine1())
                    || !hasText(address.getCity()) || !hasText(address.getState())
                    || !hasText(address.getPostalCode()) || !hasText(address.getCountry())) {
                throw new BusinessRuleException("Complete delivery address is required before payment");
            }
        }
        if (order.getPaymentAmount() == null || order.getPaymentAmount().signum() <= 0) {
            throw new BusinessRuleException("Order payment amount must be positive");
        }
        if (!hasText(paymentProperties.getCurrency()) || !paymentProperties.getCurrency().matches("[A-Z]{3}")
                || !paymentProperties.getCurrency().equals(shipment.getCurrency())) {
            throw new BusinessRuleException("Shipping and payment currency must match");
        }
    }

    private PaymentGatewayRefundRequest refundRequest(Payment payment) {
        return new PaymentGatewayRefundRequest(payment.getOrder().getId(), payment.getId(), payment.getExternalId(),
                payment.getAmount(), payment.getCurrency(), payment.getRefundOperationKey());
    }
    private void recordEvent(Payment payment, String eventId, PaymentStatus status, String source, String details) {
        paymentEventRepository.save(PaymentEvent.builder().payment(payment).externalEventId(eventId)
                .newStatus(status).source(source).details(details).build());
    }
    private boolean hasText(String value) { return value != null && !value.isBlank(); }
    private record CreateCandidate(PaymentGatewayCreateRequest request, Instant preparedAt) {}
    private record PreparedPayment(PaymentGatewayCreateRequest request, PaymentResponse replay) {}
    private record PreparedRefund(PaymentGatewayRefundRequest request, PaymentResponse replay) {}
    private record RefundCandidate(PaymentGatewayRefundRequest request, String externalRefundId) {}
}
