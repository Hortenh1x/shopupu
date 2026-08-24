package com.example.shopupu.payments.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.payments.entity.Payment;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.gateway.PaymentGatewayClient;
import com.example.shopupu.payments.repository.PaymentRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationJobTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewayClient paymentGatewayClient;

    private SimpleMeterRegistry meterRegistry;
    private PaymentReconciliationJob job;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        job = new PaymentReconciliationJob(paymentRepository, paymentGatewayClient, meterRegistry);
    }

    @Test
    void countsMismatchWhenProviderSucceededButLocalIsPending() {
        Payment payment = payment(10L, PaymentStatus.PENDING, "inv-1");
        when(paymentRepository.findTop100ByStatusInAndExternalIdIsNotNullAndCreatedAtBetween(
                anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(payment));
        when(paymentGatewayClient.fetchPaymentStatus("inv-1"))
                .thenReturn(Optional.of(PaymentStatus.SUCCEEDED));

        job.reconcilePayments();

        assertEquals(1.0, mismatchCount());
    }

    @Test
    void countsMismatchWhenLocallyExpiredPaymentSucceededAtProvider() {
        Payment payment = payment(11L, PaymentStatus.EXPIRED, "inv-2");
        when(paymentRepository.findTop100ByStatusInAndExternalIdIsNotNullAndCreatedAtBetween(
                anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(payment));
        when(paymentGatewayClient.fetchPaymentStatus("inv-2"))
                .thenReturn(Optional.of(PaymentStatus.SUCCEEDED));

        job.reconcilePayments();

        assertEquals(1.0, mismatchCount());
    }

    @Test
    void inFlightSkewIsNotAMismatch() {
        Payment payment = payment(12L, PaymentStatus.CREATED, "inv-3");
        when(paymentRepository.findTop100ByStatusInAndExternalIdIsNotNullAndCreatedAtBetween(
                anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(payment));
        when(paymentGatewayClient.fetchPaymentStatus("inv-3"))
                .thenReturn(Optional.of(PaymentStatus.PENDING));

        job.reconcilePayments();

        assertEquals(0.0, mismatchCount());
    }

    @Test
    void providerWithoutStatusApiIsSkipped() {
        Payment payment = payment(13L, PaymentStatus.PENDING, "inv-4");
        when(paymentRepository.findTop100ByStatusInAndExternalIdIsNotNullAndCreatedAtBetween(
                anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of(payment));
        when(paymentGatewayClient.fetchPaymentStatus("inv-4")).thenReturn(Optional.empty());

        job.reconcilePayments();

        assertEquals(0.0, mismatchCount());
    }

    @Test
    void noCandidatesMeansNoProviderCalls() {
        when(paymentRepository.findTop100ByStatusInAndExternalIdIsNotNullAndCreatedAtBetween(
                anyCollection(), any(Instant.class), any(Instant.class)))
                .thenReturn(List.of());

        job.reconcilePayments();

        verify(paymentGatewayClient, never()).fetchPaymentStatus(any());
    }

    @Test
    void discrepancyTableCoversEquivalenceClasses() {
        // settled the same way — never a discrepancy
        assertFalse(PaymentReconciliationJob.isDiscrepancy(PaymentStatus.SUCCEEDED, PaymentStatus.SUCCEEDED));
        // both still in flight
        assertFalse(PaymentReconciliationJob.isDiscrepancy(PaymentStatus.CREATED, PaymentStatus.PENDING));
        assertFalse(PaymentReconciliationJob.isDiscrepancy(PaymentStatus.PENDING, PaymentStatus.CREATED));
        // both terminal with no money moved
        assertFalse(PaymentReconciliationJob.isDiscrepancy(PaymentStatus.EXPIRED, PaymentStatus.FAILED));
        assertFalse(PaymentReconciliationJob.isDiscrepancy(PaymentStatus.FAILED, PaymentStatus.CANCELED));
        // money moved on one side only — always a discrepancy
        assertTrue(PaymentReconciliationJob.isDiscrepancy(PaymentStatus.PENDING, PaymentStatus.SUCCEEDED));
        assertTrue(PaymentReconciliationJob.isDiscrepancy(PaymentStatus.EXPIRED, PaymentStatus.SUCCEEDED));
        assertTrue(PaymentReconciliationJob.isDiscrepancy(PaymentStatus.PENDING, PaymentStatus.FAILED));
        assertTrue(PaymentReconciliationJob.isDiscrepancy(PaymentStatus.PENDING, PaymentStatus.REFUNDED));
    }

    private double mismatchCount() {
        return meterRegistry.counter("shopupu.payments", "result", "reconciliation_mismatch").count();
    }

    private Payment payment(Long id, PaymentStatus status, String externalId) {
        Order order = new Order();
        order.setId(id + 100);
        order.setOrderNumber("ORD-20260810-R" + id);
        order.setUser(User.builder().id(1L).email("user@example.com").build());
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setPaymentAmount(new BigDecimal("24.99"));
        return Payment.builder()
                .id(id)
                .order(order)
                .amount(order.getPaymentAmount())
                .currency("UAH")
                .provider("monobank")
                .status(status)
                .externalId(externalId)
                .idempotencyKey("key-" + id)
                .build();
    }
}
