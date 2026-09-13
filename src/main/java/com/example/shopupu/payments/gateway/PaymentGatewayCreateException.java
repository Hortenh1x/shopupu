package com.example.shopupu.payments.gateway;

/** Explicit provider proof that no payment was created. Never use for timeout/unknown outcomes. */
public class PaymentGatewayCreateException extends RuntimeException {
    public PaymentGatewayCreateException(String message) { super(message); }
    public PaymentGatewayCreateException(String message, Throwable cause) { super(message, cause); }
}
