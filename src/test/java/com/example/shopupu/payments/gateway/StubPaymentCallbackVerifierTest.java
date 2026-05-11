package com.example.shopupu.payments.gateway;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * describes the StubPaymentCallbackVerifierTest test class.
 */
class StubPaymentCallbackVerifierTest {

    // handles isValid.
    @Test
    void isValidAcceptsCallbacksWhenSecretIsBlank() {
        var verifier = new StubPaymentCallbackVerifier("");

        assertTrue(verifier.isValid("payload", null));
    }

    // handles isValid.
    @Test
    void isValidRequiresMatchingSecretWhenConfigured() {
        var verifier = new StubPaymentCallbackVerifier("secret");

        assertTrue(verifier.isValid("payload", "secret"));
        assertFalse(verifier.isValid("payload", "wrong"));
    }
}
