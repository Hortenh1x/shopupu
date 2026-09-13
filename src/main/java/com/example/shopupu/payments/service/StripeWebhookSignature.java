package com.example.shopupu.payments.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Stripe v1 signs the exact UTF-8 request body prefixed by its Unix timestamp. */
public final class StripeWebhookSignature {
    private StripeWebhookSignature() {}

    public static boolean isValid(String secret, String payload, String header, Clock clock) {
        if (secret == null || !secret.startsWith("whsec_") || payload == null || header == null) return false;
        if (payload.length() > 262_144 || header.length() > 4096) return false;
        try {
            String timestamp = null;
            for (String item : header.split(",")) {
                String part = item.trim();
                if (part.startsWith("t=")) {
                    if (timestamp != null) return false;
                    timestamp = part.substring(2);
                }
            }
            if (timestamp == null || !timestamp.matches("[0-9]{1,12}")) return false;
            long time = Long.parseLong(timestamp);
            long now = clock.instant().getEpochSecond();
            if (time < now - 300 || time > now + 300) return false;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            boolean valid = false;
            for (String item : header.split(",")) {
                String part = item.trim();
                if (part.startsWith("v1=") && part.substring(3).matches("[0-9a-fA-F]{64}")) {
                    valid |= MessageDigest.isEqual(expected, HexFormat.of().parseHex(part.substring(3)));
                }
            }
            return valid;
        } catch (java.security.GeneralSecurityException | IllegalArgumentException ex) {
            return false;
        }
    }
}
