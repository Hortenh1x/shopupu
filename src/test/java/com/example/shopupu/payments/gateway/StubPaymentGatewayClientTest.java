package com.example.shopupu.payments.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shopupu.payments.entity.PaymentStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * describes the StubPaymentGatewayClientTest test class.
 */
class StubPaymentGatewayClientTest {

    // handles createPayment.
    @Test
    void createPaymentReturnsPendingStubResponse() {
        StubPaymentGatewayClient client = new StubPaymentGatewayClient();

        var response = client.createPayment(new PaymentGatewayCreateRequest(
                1L,
                2L,
                new BigDecimal("10.00"),
                "EUR"
        ));

        assertTrue(response.externalPaymentId().startsWith("stub-payment-1-"));
        assertEquals("stub", response.provider());
        assertEquals(PaymentStatus.PENDING, response.status());
        assertNotNull(response.paymentUrl());
        assertNotNull(response.clientToken());
    }

    @Test
    void refundRecoveryWithoutResponseIdIsDeterministicAcrossInstances() {
        var request = new PaymentGatewayRefundRequest(1L, 2L, "stub-payment-1-test", new BigDecimal("10.00"), "EUR", "stable-operation");
        var recoveredBeforePost = new StubPaymentGatewayClient().fetchRefundStatus(request, null).orElseThrow();
        var submitted = new StubPaymentGatewayClient().refundPayment(request);
        var recoveredAfterRestart = new StubPaymentGatewayClient().fetchRefundStatus(request, submitted.externalRefundId()).orElseThrow();
        assertEquals(PaymentGatewayRefundStatus.SUCCEEDED, recoveredBeforePost.status());
        assertEquals(submitted, recoveredBeforePost);
        assertEquals(submitted, recoveredAfterRestart);
    }

    @Test
    void refundRecoveryRejectsAnUnrelatedRefundIdentity() {
        var request = new PaymentGatewayRefundRequest(1L, 2L, "stub-payment-1-test", new BigDecimal("10.00"), "EUR", "stable-operation");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new StubPaymentGatewayClient().fetchRefundStatus(request, "other-refund"));
    }
}
