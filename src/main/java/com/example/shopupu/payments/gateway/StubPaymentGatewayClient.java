package com.example.shopupu.payments.gateway;

import com.example.shopupu.payments.entity.PaymentStatus;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

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

    @Override
    // stub provider accepts every refund so the flow is testable locally
    public boolean refundPayment(String externalPaymentId) {
        return true;
    }
}
