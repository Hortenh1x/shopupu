package com.example.shopupu.payments.gateway;

import com.example.shopupu.payments.entity.PaymentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "stub", matchIfMissing = true)
/**
 * describes the StubPaymentGatewayClient class.
 */
public class StubPaymentGatewayClient implements PaymentGatewayClient {

    @Override
    // handles createPayment.
    public PaymentGatewayCreateResponse createPayment(PaymentGatewayCreateRequest request) {
        String externalId = "stub-payment-" + request.orderId() + "-" + UUID.randomUUID();
        return new PaymentGatewayCreateResponse(
                externalId,
                "stub",
                PaymentStatus.PENDING,
                "/payments/stub/" + externalId,
                UUID.randomUUID().toString()
        );
    }
}
