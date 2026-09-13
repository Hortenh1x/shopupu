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
    public PaymentGatewayRefundResponse refundPayment(PaymentGatewayRefundRequest request) {
        return refundOutcome(request);
    }

    /**
     * The local stub has no external money movement: a persisted operation defines
     * its deterministic full refund. Recovery also covers a crash before create/response apply.
     */
    @Override
    public java.util.Optional<PaymentGatewayRefundResponse> fetchRefundStatus(
            PaymentGatewayRefundRequest request, String externalRefundId) {
        PaymentGatewayRefundResponse outcome = refundOutcome(request);
        if (externalRefundId != null && !externalRefundId.isBlank()
                && !outcome.externalRefundId().equals(externalRefundId)) {
            throw new IllegalArgumentException("Refund identity does not match the stub operation");
        }
        return java.util.Optional.of(outcome);
    }

    private PaymentGatewayRefundResponse refundOutcome(PaymentGatewayRefundRequest request) {
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank()) {
            throw new IllegalArgumentException("A stable refund operation key is required");
        }
        return new PaymentGatewayRefundResponse("stub-refund-" + request.idempotencyKey(), PaymentGatewayRefundStatus.SUCCEEDED);
    }

    @Override
    // stub provider accepts every refund so the flow is testable locally
    public boolean refundPayment(String externalPaymentId) {
        return true;
    }
}
