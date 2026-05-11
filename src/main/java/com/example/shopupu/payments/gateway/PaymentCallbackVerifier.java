package com.example.shopupu.payments.gateway;

/**
 * describes the PaymentCallbackVerifier interface.
 */
public interface PaymentCallbackVerifier {
    boolean isValid(String payload, String signature);
}
