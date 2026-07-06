package com.example.shopupu.payments.gateway;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StubPaymentCallbackVerifierTest {

    @Test
    void isValidRejectsEverythingWhenSecretIsBlank() {
        var verifier = new StubPaymentCallbackVerifier("");

        assertFalse(verifier.isValid("payload", null));
        assertFalse(verifier.isValid("payload", "anything"));
    }

    @Test
    void isValidRequiresMatchingHmacWhenConfigured() {
        var verifier = new StubPaymentCallbackVerifier("secret");

        String goodSignature = HmacSignature.sign("secret", "payload");
        assertTrue(verifier.isValid("payload", goodSignature));
        assertFalse(verifier.isValid("payload", "wrong"));
        assertFalse(verifier.isValid("payload", null));
        assertFalse(verifier.isValid("tampered", goodSignature));
    }
}
