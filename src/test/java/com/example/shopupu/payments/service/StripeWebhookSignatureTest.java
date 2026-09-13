package com.example.shopupu.payments.service;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class StripeWebhookSignatureTest {
    private static final String SECRET = "whsec_fixture";
    private static final String PAYLOAD = "{\"livemode\":false,\"id\":\"evt_fixture\"}";
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(1_800_000_000L), ZoneOffset.UTC);

    @Test
    void acceptsCurrentV1AmongRotatingSignatures() throws Exception {
        String header = signature(CLOCK.instant().getEpochSecond());
        assertTrue(StripeWebhookSignature.isValid(SECRET, PAYLOAD, "v1=" + "0".repeat(64) + "," + header, CLOCK));
    }

    @Test
    void rejectsChangedPayloadMissingSecretAndUnsupportedSignature() throws Exception {
        String header = signature(CLOCK.instant().getEpochSecond());
        assertFalse(StripeWebhookSignature.isValid(SECRET, PAYLOAD + " ", header, CLOCK));
        assertFalse(StripeWebhookSignature.isValid("", PAYLOAD, header, CLOCK));
        assertFalse(StripeWebhookSignature.isValid(SECRET, PAYLOAD, header.replace("v1=", "v0="), CLOCK));
    }

    @Test
    void rejectsOldFutureDuplicateAndMalformedTimestamps() throws Exception {
        assertFalse(StripeWebhookSignature.isValid(SECRET, PAYLOAD, signature(1_799_999_699L), CLOCK));
        assertFalse(StripeWebhookSignature.isValid(SECRET, PAYLOAD, signature(1_800_000_301L), CLOCK));
        assertFalse(StripeWebhookSignature.isValid(SECRET, PAYLOAD, "t=1800000000," + signature(1_800_000_000L), CLOCK));
        assertFalse(StripeWebhookSignature.isValid(SECRET, PAYLOAD, "t=-9223372036854775808,v1=invalid", CLOCK));
    }

    private String signature(long timestamp) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t=" + timestamp + ",v1=" + HexFormat.of().formatHex(mac.doFinal((timestamp + "." + PAYLOAD).getBytes(StandardCharsets.UTF_8)));
    }
}
