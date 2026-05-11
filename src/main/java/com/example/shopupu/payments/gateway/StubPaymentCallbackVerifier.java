package com.example.shopupu.payments.gateway;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "stub", matchIfMissing = true)
/**
 * describes the StubPaymentCallbackVerifier class.
 */
public class StubPaymentCallbackVerifier implements PaymentCallbackVerifier {

    private final String callbackSecret;

    // handles StubPaymentCallbackVerifier.
    public StubPaymentCallbackVerifier(@Value("${payments.callback-secret:}") String callbackSecret) {
        this.callbackSecret = callbackSecret;
    }

    @Override
    // handles isValid.
    public boolean isValid(String payload, String signature) {
        if (callbackSecret == null || callbackSecret.isBlank()) {
            return true;
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                callbackSecret.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
        );
    }
}
