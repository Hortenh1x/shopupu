package com.example.shopupu.payments.service;

import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
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
import com.example.shopupu.payments.gateway.PaymentCallbackVerifier;
import com.example.shopupu.payments.gateway.PaymentGatewayClient;
import com.example.shopupu.payments.gateway.PaymentGatewayCreateRequest;
import com.example.shopupu.payments.gateway.PaymentGatewayCreateResponse;
import com.example.shopupu.payments.mapper.PaymentMapper;
import com.example.shopupu.payments.repository.PaymentEventRepository;
import com.example.shopupu.payments.repository.PaymentRepository;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    public PaymentResponse createPayment(Long orderId) {
        return createPayment(orderId, null);
    }

    /**
     * Deliberately NOT one big transaction (ARCH-10/DB-04): the local payment row
     * is committed first, then the gateway HTTP call happens without holding a DB
     * connection, then the result is applied in a second short transaction.
     * A client Idempotency-Key makes retries return the same payment (PAY-02).
     */
    public PaymentResponse createPayment(Long orderId, String clientIdempotencyKey) {
        if (clientIdempotencyKey != null && !clientIdempotencyKey.isBlank()) {
            var existing = paymentRepository.findByIdempotencyKey(clientIdempotencyKey);
            if (existing.isPresent()) {
                return paymentMapper.toResponse(existing.get());
            }
        }

        Payment payment = transactionTemplate.execute(tx -> preparePayment(orderId, clientIdempotencyKey));

        PaymentGatewayCreateResponse gatewayResponse;
        try {
            gatewayResponse = paymentGatewayClient.createPayment(new PaymentGatewayCreateRequest(
                    payment.getOrder().getId(),
                    payment.getId(),
                    payment.getAmount(),
                    payment.getCurrency()
            ));
        } catch (Exception ex) {
            log.warn("Payment gateway call failed for payment {}", payment.getId(), ex);
            transactionTemplate.executeWithoutResult(tx -> markGatewayFailure(payment.getId()));
            throw new BusinessRuleException("Payment provider is unavailable, please try again");
        }

        return transactionTemplate.execute(tx -> applyGatewayResult(payment.getId(), gatewayResponse));
    }

    private Payment preparePayment(Long orderId, String clientIdempotencyKey) {
        Order order = findOrder(orderId);
        accessControlService.requireOrderOwnerOrAdmin(order);

        validateOrderCanBePaid(order);
        validatePaymentAttemptAllowed(order);

        Payment payment = Payment.builder()
                .order(order)
                .amount(order.getPaymentAmount())
                .provider(paymentProperties.getDefaultProvider())
                .status(PaymentStatus.CREATED)
                .idempotencyKey(clientIdempotencyKey != null && !clientIdempotencyKey.isBlank()
                        ? clientIdempotencyKey
                        : UUID.randomUUID().toString())
                .currency(paymentProperties.getCurrency())
                .build();
        paymentRepository.save(payment);
        recordEvent(payment, null, payment.getStatus(), "SYSTEM", "Payment created");
        return payment;
    }

    private void markGatewayFailure(Long paymentId) {
        paymentRepository.findById(paymentId).ifPresent(payment -> {
            payment.setStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);
            recordEvent(payment, null, PaymentStatus.FAILED, "SYSTEM", "Gateway call failed");
        });
    }

    private PaymentResponse applyGatewayResult(Long paymentId, PaymentGatewayCreateResponse gatewayResponse) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        payment.setStatus(gatewayResponse.status());
        payment.setProvider(gatewayResponse.provider());
        payment.setExternalId(gatewayResponse.externalPaymentId());
        payment.setPaymentUrl(gatewayResponse.paymentUrl());
        payment.setClientToken(gatewayResponse.clientToken());
        paymentRepository.save(payment);

        recordEvent(payment, null, payment.getStatus(), "SYSTEM", "Payment registered at provider");

        orderService.markPendingPayment(payment.getOrder().getId());

        return paymentMapper.toResponse(payment);
    }

    @Transactional
    public void handleCallback(PaymentCallbackRequest callback, String rawPayload, String signature) {
        if (!paymentCallbackVerifier.isValid(rawPayload, signature)) {
            throw new ForbiddenOperationException("Invalid payment callback signature");
        }

        if (isDuplicateCallback(callback.externalEventId())) {
            log.info("Duplicate payment callback ignored: {}", callback.externalEventId());
            return;
        }

        Payment payment = findPaymentByExternalId(callback.externalPaymentId());
        PaymentStatus newStatus = callback.status();

        if (payment.getStatus() == newStatus) {
            recordEvent(payment, callback.externalEventId(), newStatus, "PAYMENT_CALLBACK", callback.details());
            log.info("Duplicate payment status ignored for payment {}", callback.externalPaymentId());
            return;
        }

        if (!payment.getStatus().canTransitionTo(newStatus)) {
            recordEvent(payment, callback.externalEventId(), payment.getStatus(), "PAYMENT_CALLBACK",
                    "Rejected illegal transition " + payment.getStatus() + " -> " + newStatus);
            log.warn("Rejected illegal payment status transition {} -> {} for payment {}",
                    payment.getStatus(), newStatus, payment.getId());
            return;
        }

        payment.setStatus(newStatus);
        paymentRepository.save(payment);

        recordEvent(payment, callback.externalEventId(), newStatus, "PAYMENT_CALLBACK", callback.details());

        Long orderId = payment.getOrder().getId();
        if (newStatus == PaymentStatus.SUCCEEDED) {
            meterRegistry.counter("shopupu.payments", "result", "succeeded").increment();
            orderService.markPaidFromPayment(orderId);
            log.info("Order {} marked as PAID", orderId);
        } else if (newStatus == PaymentStatus.FAILED
                || newStatus == PaymentStatus.CANCELED
                || newStatus == PaymentStatus.EXPIRED) {
            meterRegistry.counter("shopupu.payments", "result", "failed").increment();
            orderService.onPaymentFailed(orderId);
        }
    }

    /** Marks stale unfinished payments EXPIRED so late callbacks are rejected (PAY-04). */
    @Transactional
    public int expireStalePayments(java.time.Instant cutoff) {
        var stale = paymentRepository.findTop100ByStatusInAndCreatedAtBefore(
                java.util.EnumSet.of(PaymentStatus.CREATED, PaymentStatus.PENDING), cutoff);
        for (Payment payment : stale) {
            payment.setStatus(PaymentStatus.EXPIRED);
            paymentRepository.save(payment);
            recordEvent(payment, null, PaymentStatus.EXPIRED, "SYSTEM", "Payment expired by timeout");
            orderService.onPaymentFailed(payment.getOrder().getId());
        }
        return stale.size();
    }

    public void handleCallback(PaymentCallbackRequest callback, String signature) {
        handleCallback(callback, "", signature);
    }

    /** Full refund (PAY-05/ORD-07): provider first, then local payment + order + stock. */
    @Transactional
    public PaymentResponse refundPayment(Long paymentId) {
        accessControlService.requireAdmin();
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (payment.getStatus() != PaymentStatus.SUCCEEDED) {
            throw new BusinessRuleException("Only succeeded payments can be refunded");
        }

        boolean accepted;
        try {
            accepted = paymentGatewayClient.refundPayment(payment.getExternalId());
        } catch (UnsupportedOperationException ex) {
            throw new BusinessRuleException("Refunds are not supported by the current payment provider");
        }
        if (!accepted) {
            throw new BusinessRuleException("Payment provider rejected the refund");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        paymentRepository.save(payment);
        recordEvent(payment, null, PaymentStatus.REFUNDED, "ADMIN", "Refund executed");

        orderService.markRefunded(payment.getOrder().getId(), accessControlService.currentEmail());
        auditService.record(accessControlService.currentEmail(), "PAYMENT_REFUNDED",
                "payment", String.valueOf(payment.getId()), "amount=" + payment.getAmount());
        return paymentMapper.toResponse(payment);
    }

    private void recordEvent(Payment payment, String externalEventId, PaymentStatus status, String source, String details) {
        PaymentEvent event = PaymentEvent.builder()
                .payment(payment)
                .externalEventId(externalEventId)
                .newStatus(status)
                .source(source)
                .details(details)
                .build();
        paymentEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPaymentForCurrentUser(Long id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));
        accessControlService.requireOrderOwnerOrAdmin(payment.getOrder());
        return paymentMapper.toResponse(payment);
    }

    private void validateOrderCanBePaid(Order order) {
        if (order.getStatus() == OrderStatus.PAID) {
            throw new BusinessRuleException("Order is already paid");
        }
        if (order.getStatus() != OrderStatus.CREATED && order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessRuleException("Only unpaid orders can be paid");
        }
        if (shipmentRepository.findByOrder(order).isEmpty()) {
            throw new BusinessRuleException("Shipping must be selected before payment");
        }
        if (order.getPaymentAmount() == null || order.getPaymentAmount().signum() < 0) {
            throw new BusinessRuleException("Order payment amount is invalid");
        }
    }

    private void validatePaymentAttemptAllowed(Order order) {
        Optional<Payment> latestPayment = paymentRepository.findTopByOrderOrderByCreatedAtDesc(order);
        if (latestPayment.isEmpty()) {
            return;
        }

        PaymentStatus status = latestPayment.get().getStatus();
        if (status == PaymentStatus.CREATED || status == PaymentStatus.PENDING) {
            throw new BusinessRuleException("Payment is already in progress");
        }
        if (status == PaymentStatus.SUCCEEDED) {
            throw new BusinessRuleException("Order is already paid");
        }
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    private Payment findPaymentByExternalId(String externalPaymentId) {
        return paymentRepository.findByExternalId(externalPaymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for callback: " + externalPaymentId));
    }

    private boolean isDuplicateCallback(String externalEventId) {
        if (externalEventId == null || externalEventId.isBlank()) {
            return false;
        }
        return paymentEventRepository.findByExternalEventId(externalEventId).isPresent();
    }
}
