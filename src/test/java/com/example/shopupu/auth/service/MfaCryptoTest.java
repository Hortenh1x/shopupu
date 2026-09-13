package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.config.MfaProperties;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class MfaCryptoTest {
    private MfaCrypto crypto() {
        var props = new MfaProperties();
        props.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        return new MfaCrypto(props);
    }
    @Test void totpMatchesRfc6238Sha1VectorWithSixDigitsAndRejectsReplay() {
        var crypto = crypto();
        String secret = crypto.base32("12345678901234567890".getBytes(StandardCharsets.US_ASCII));
        assertEquals(1, crypto.acceptedStep(secret, "287082", Instant.ofEpochSecond(59), -1));
        assertEquals(-1, crypto.acceptedStep(secret, "287082", Instant.ofEpochSecond(59), 1));
        assertEquals(-1, crypto.acceptedStep(secret, "000000", Instant.ofEpochSecond(59), -1));
    }
    @Test void encryptsWithFreshIvAndBindsCiphertextToAccount() {
        var crypto = crypto();
        String secret = crypto.newSecret();
        String first = crypto.encrypt(secret, 1L);
        assertNotEquals(first, crypto.encrypt(secret, 1L));
        assertEquals(secret, crypto.decrypt(first, 1L));
        assertThrows(IllegalStateException.class, () -> crypto.decrypt(first, 2L));
    }
}
