package com.example.shopupu.payments.gateway;

/**
 * describes the PaymentGatewayClient interface.
 */
public interface PaymentGatewayClient {
    PaymentGatewayCreateResponse createPayment(PaymentGatewayCreateRequest request);
}