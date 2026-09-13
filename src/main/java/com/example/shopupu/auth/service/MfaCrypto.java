package com.example.shopupu.auth.service;

import com.example.shopupu.common.exception.ServiceUnavailableException;
import com.example.shopupu.config.MfaProperties;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class MfaCrypto {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] encryptionKey;
    public MfaCrypto(MfaProperties props) {
        if (!props.isEncryptionKeyValid()) throw new IllegalArgumentException("Invalid MFA encryption key configuration");
        encryptionKey = props.getEncryptionKey() == null || props.getEncryptionKey().isBlank()
                ? null : Base64.getDecoder().decode(props.getEncryptionKey());
    }
    public void requireAvailable() {
        if (encryptionKey == null) throw new ServiceUnavailableException("MFA_CONFIGURATION_REQUIRED",
                "Privileged sign-in requires MFA encryption key configuration");
    }
    public String newSecret() {
        requireAvailable();
        byte[] bytes = new byte[20]; RANDOM.nextBytes(bytes); return base32(bytes);
    }
    public String recoveryCode() {
        byte[] bytes = new byte[16]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public String encrypt(String secret, Long userId) {
        requireAvailable();
        byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
        try {
            Cipher cipher = cipher(Cipher.ENCRYPT_MODE, iv, userId);
            byte[] encrypted = cipher.doFinal(secret.getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length)
                    .put(iv).put(encrypted).array());
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("MFA encryption failed", ex); }
    }
    public String decrypt(String encrypted, Long userId) {
        requireAvailable();
        try {
            byte[] bytes = Base64.getDecoder().decode(encrypted);
            if (bytes.length < 29) throw new GeneralSecurityException("Invalid ciphertext");
            return new String(cipher(Cipher.DECRYPT_MODE, Arrays.copyOf(bytes, 12), userId)
                    .doFinal(Arrays.copyOfRange(bytes, 12, bytes.length)), StandardCharsets.US_ASCII);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("MFA decryption failed");
        }
    }
    private Cipher cipher(int mode, byte[] iv, Long userId) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(mode, new SecretKeySpec(encryptionKey, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD(("shopupu:mfa:" + userId).getBytes(StandardCharsets.US_ASCII));
        return cipher;
    }
    /** Returns the matching step or -1. A step can be accepted at most once per factor. */
    public long acceptedStep(String secret, String code, Instant now, long lastAccepted) {
        if (code == null || !code.matches("[0-9]{6}")) return -1;
        long current = now.getEpochSecond() / 30;
        for (long step = current - 1; step <= current + 1; step++) {
            if (step > lastAccepted && MessageDigest.isEqual(totp(secret, step).getBytes(StandardCharsets.US_ASCII),
                    code.getBytes(StandardCharsets.US_ASCII))) return step;
        }
        return -1;
    }
    String totp(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(decodeBase32(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 15;
            int value = ByteBuffer.wrap(hash, offset, 4).getInt() & 0x7fffffff;
            return String.format(java.util.Locale.ROOT, "%06d", value % 1_000_000);
        } catch (GeneralSecurityException ex) { throw new IllegalStateException("TOTP unavailable", ex); }
    }
    String base32(byte[] input) {
        StringBuilder result = new StringBuilder(); int buffer = 0, bits = 0;
        for (byte value : input) {
            buffer = (buffer << 8) | (value & 255); bits += 8;
            while (bits >= 5) { bits -= 5; result.append(ALPHABET.charAt((buffer >> bits) & 31)); }
        }
        if (bits > 0) result.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return result.toString();
    }
    private byte[] decodeBase32(String input) {
        byte[] result = new byte[input.length() * 5 / 8]; int buffer = 0, bits = 0, index = 0;
        for (char value : input.toCharArray()) {
            int digit = ALPHABET.indexOf(value);
            if (digit < 0) throw new IllegalArgumentException("Invalid TOTP secret");
            buffer = (buffer << 5) | digit; bits += 5;
            if (bits >= 8) { bits -= 8; result[index++] = (byte) (buffer >> bits); }
        }
        return result;
    }
}
