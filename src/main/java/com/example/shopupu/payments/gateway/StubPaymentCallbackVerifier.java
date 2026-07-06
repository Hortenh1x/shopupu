package com.example.shopupu.payments.gateway;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "payments.default-provider", havingValue = "stub", matchIfMissing = true)
/**
 * Dev/test verifier. Fail-closed: without a configured secret every callback is
 * rejected, so a default configuration can never accept forged payment callbacks.
 */
public class StubPaymentCallbackVerifier implements PaymentCallbackVerifier {

    private final String callbackSecret;

    public StubPaymentCallbackVerifier(@Value("${payments.callback-secret:}") String callbackSecret) {
        this.callbackSecret = callbackSecret;
    }

    @Override
    public boolean isValid(String payload, String signature) {
        if (callbackSecret == null || callbackSecret.isBlank()) {
            return false;
        }
        if (signature == null || signature.isBlank()) {
            return false;
        }
        return HmacSignature.matches(HmacSignature.sign(callbackSecret, payload), signature);
    }
}
