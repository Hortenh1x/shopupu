package com.example.shopupu.payments.gateway;

import com.example.shopupu.payments.entity.PaymentStatus;
import java.util.Optional;

public interface PaymentGatewayClient {

    PaymentGatewayCreateResponse createPayment(PaymentGatewayCreateRequest request);

    /**
     * Requests a refund at the provider. Default: not supported by this gateway.
     *
     * @return true when the provider accepted the refund request
     */
    default boolean refundPayment(String externalPaymentId) {
        throw new UnsupportedOperationException("Refunds are not supported by this payment provider");
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
