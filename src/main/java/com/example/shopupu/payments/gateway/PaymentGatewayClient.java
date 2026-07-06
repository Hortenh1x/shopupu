package com.example.shopupu.payments.gateway;

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
}
