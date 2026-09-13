package com.example.shopupu.payments.gateway;

import com.example.shopupu.payments.entity.PaymentStatus;
import java.util.Optional;

public interface PaymentGatewayClient {

    /** Local configuration validation before persisting a new attempt. Must never perform HTTP. */
    default void ensureAvailable() {}

    /** Local, secret-free snapshot of the exact create body; persisted before any HTTP. */
    default String snapshotCreateContext(PaymentGatewayCreateRequest request) { return null; }

    PaymentGatewayCreateResponse createPayment(PaymentGatewayCreateRequest request);

    /** Replay only the immutable original request within the provider's safe idempotency window. */
    default Optional<PaymentGatewayCreateResponse> recoverPayment(PaymentGatewayCreateRequest original,
            java.time.Instant preparedAt, java.time.Instant retryUntil) {
        return Optional.empty();
    }

    /**
     * Requests a refund at the provider. Default: not supported by this gateway.
     *
     * @return true when the provider accepted the refund request
     */
    default boolean refundPayment(String externalPaymentId) {
        throw new UnsupportedOperationException("Refunds are not supported by this payment provider");
    }

    /** Accepted requests are pending until the adapter verifies a confirmed full refund. */
    default PaymentGatewayRefundResponse refundPayment(PaymentGatewayRefundRequest request) {
        throw new UnsupportedOperationException("Refunds are not supported by this payment provider");
    }

    /** Read-only recovery; unknown operations must never be blindly resubmitted. */
    default Optional<PaymentGatewayRefundResponse> fetchRefundStatus(
            PaymentGatewayRefundRequest request, String externalRefundId) {
        return Optional.empty();
    }

    /** Adapters validate provider identity, amount and currency against this expected snapshot. */
    default Optional<PaymentStatus> fetchPaymentStatus(PaymentGatewayStatusRequest request) {
        return fetchPaymentStatus(request.externalPaymentId());
    }

    /**
     * Queries the provider for the current status of a payment, mapped to the local
     * {@link PaymentStatus}. Empty when the provider does not support status queries,
     * returned an unknown status, or the call failed — callers must treat empty as
     * "no information", never as a discrepancy.
     */
    default Optional<PaymentStatus> fetchPaymentStatus(String externalPaymentId) {
        return Optional.empty();
    }
}
