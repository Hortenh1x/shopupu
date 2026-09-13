package com.example.shopupu.payments.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.payments.dto.PaymentReconciliationCandidate;
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
        var properties = new com.example.shopupu.config.PaymentProperties();
        properties.setDefaultProvider("stub");
        job = new PaymentReconciliationJob(paymentRepository, paymentGatewayClient, meterRegistry, properties);
    }

    @Test
    void countsMismatchWhenProviderSucceededButLocalIsPending() {
        PaymentReconciliationCandidate payment = payment(10L, PaymentStatus.PENDING, "inv-1");
        when(paymentRepository.findReconciliationCandidates(
                org.mockito.ArgumentMatchers.eq("stub"), anyCollection(), any(Instant.class), any(Instant.class),
                org.mockito.ArgumentMatchers.anyLong(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payment));
        when(paymentGatewayClient.fetchPaymentStatus(any(com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest.class)))
                .thenReturn(Optional.of(PaymentStatus.SUCCEEDED));

        job.reconcilePayments();

        assertEquals(1.0, mismatchCount());
    }

    @Test
    void countsMismatchWhenLocallyExpiredPaymentSucceededAtProvider() {
        PaymentReconciliationCandidate payment = payment(11L, PaymentStatus.EXPIRED, "inv-2");
        when(paymentRepository.findReconciliationCandidates(
                org.mockito.ArgumentMatchers.eq("stub"), anyCollection(), any(Instant.class), any(Instant.class),
                org.mockito.ArgumentMatchers.anyLong(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payment));
        when(paymentGatewayClient.fetchPaymentStatus(any(com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest.class)))
                .thenReturn(Optional.of(PaymentStatus.SUCCEEDED));

        job.reconcilePayments();

        assertEquals(1.0, mismatchCount());
    }

    @Test
    void inFlightSkewIsNotAMismatch() {
        PaymentReconciliationCandidate payment = payment(12L, PaymentStatus.CREATED, "inv-3");
        when(paymentRepository.findReconciliationCandidates(
                org.mockito.ArgumentMatchers.eq("stub"), anyCollection(), any(Instant.class), any(Instant.class),
                org.mockito.ArgumentMatchers.anyLong(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payment));
        when(paymentGatewayClient.fetchPaymentStatus(any(com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest.class)))
                .thenReturn(Optional.of(PaymentStatus.PENDING));

        job.reconcilePayments();

        assertEquals(0.0, mismatchCount());
    }

    @Test
    void providerWithoutStatusApiIsSkipped() {
        PaymentReconciliationCandidate payment = payment(13L, PaymentStatus.PENDING, "inv-4");
        when(paymentRepository.findReconciliationCandidates(
                org.mockito.ArgumentMatchers.eq("stub"), anyCollection(), any(Instant.class), any(Instant.class),
                org.mockito.ArgumentMatchers.anyLong(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(payment));
        when(paymentGatewayClient.fetchPaymentStatus(any(com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest.class))).thenReturn(Optional.empty());

        job.reconcilePayments();

        assertEquals(0.0, mismatchCount());
    }

    @Test
    void noCandidatesMeansNoProviderCalls() {
        when(paymentRepository.findReconciliationCandidates(
                org.mockito.ArgumentMatchers.eq("stub"), anyCollection(), any(Instant.class), any(Instant.class),
                org.mockito.ArgumentMatchers.anyLong(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of());

        job.reconcilePayments();

        verify(paymentGatewayClient, never()).fetchPaymentStatus(any(com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest.class));
    }

    @Test
    void failedLookupDoesNotAbortLaterCandidatesAndNeverQueriesAnotherProvider() {
        var foreign = new PaymentReconciliationCandidate(1L, 101L, "stripe", "foreign", BigDecimal.TEN, "EUR", PaymentStatus.PENDING);
        when(paymentRepository.findReconciliationCandidates(org.mockito.ArgumentMatchers.eq("stub"), anyCollection(),
                any(Instant.class), any(Instant.class), org.mockito.ArgumentMatchers.anyLong(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(List.of(foreign, payment(2L, PaymentStatus.PENDING, "fails"), payment(3L, PaymentStatus.PENDING, "works")));
        when(paymentGatewayClient.fetchPaymentStatus(any(com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest.class)))
                .thenThrow(new IllegalStateException("private provider body"))
                .thenReturn(Optional.of(PaymentStatus.SUCCEEDED));
        job.reconcilePayments();
        verify(paymentGatewayClient, org.mockito.Mockito.times(2)).fetchPaymentStatus(any(com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest.class));
        assertEquals(1.0, mismatchCount());
    }

    @Test
    void cursorAdvancesBeyondAFullUnknownBatchAndWrapsWithoutUnboundedScan() {
        var firstBatch = java.util.stream.LongStream.rangeClosed(1, 100)
                .mapToObj(id -> payment(id, PaymentStatus.PENDING, "pending-" + id)).toList();
        when(paymentRepository.findReconciliationCandidates(org.mockito.ArgumentMatchers.eq("stub"), anyCollection(),
                any(Instant.class), any(Instant.class), org.mockito.ArgumentMatchers.anyLong(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(firstBatch, List.of(payment(101L, PaymentStatus.PENDING, "known")), List.of(), List.of());
        when(paymentGatewayClient.fetchPaymentStatus(any(com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest.class)))
                .thenAnswer(call -> ((com.example.shopupu.payments.gateway.PaymentGatewayStatusRequest) call.getArgument(0)).paymentId() == 101L
                        ? Optional.of(PaymentStatus.SUCCEEDED) : Optional.empty());
        job.reconcilePayments();
        job.reconcilePayments();
        job.reconcilePayments();
        var cursors = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(paymentRepository, org.mockito.Mockito.times(4)).findReconciliationCandidates(org.mockito.ArgumentMatchers.eq("stub"),
                anyCollection(), any(Instant.class), any(Instant.class), cursors.capture(), any(org.springframework.data.domain.Pageable.class));
        assertEquals(List.of(0L, 100L, 101L, 0L), cursors.getAllValues());
        assertEquals(1.0, mismatchCount());
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

    private PaymentReconciliationCandidate payment(Long id, PaymentStatus status, String externalId) {
        return new PaymentReconciliationCandidate(id, id + 100, "stub", externalId, new BigDecimal("24.99"), "EUR", status);
    }
}
