package com.example.shopupu.payments.gateway;

import com.example.shopupu.payments.entity.PaymentStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
