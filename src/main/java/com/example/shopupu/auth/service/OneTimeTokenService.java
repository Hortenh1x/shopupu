package com.example.shopupu.auth.service;

import com.example.shopupu.auth.entity.OneTimeToken;
import com.example.shopupu.auth.repository.OneTimeTokenRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.identity.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Single-use tokens (AUTH-06/07): only the SHA-256 hash is stored, minting a
 * new token invalidates the previous ones of the same purpose, consumption is
 * one-shot and TTL-bound.
 */
@Service
@RequiredArgsConstructor
public class OneTimeTokenService {

    public static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);
    public static final Duration EMAIL_VERIFICATION_TTL = Duration.ofHours(24);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final OneTimeTokenRepository tokenRepository;

    /** Returns the raw token to send to the user; only its hash is persisted. */
    @Transactional
    public String mint(User user, OneTimeToken.Purpose purpose) {
        tokenRepository.invalidateAllFor(user.getId(), purpose, Instant.now());

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        Duration ttl = purpose == OneTimeToken.Purpose.PASSWORD_RESET
                ? PASSWORD_RESET_TTL
                : EMAIL_VERIFICATION_TTL;

        tokenRepository.save(OneTimeToken.builder()
                .user(user)
                .tokenHash(hash(raw))
                .purpose(purpose)
                .expiresAt(Instant.now().plus(ttl))
                .build());
        return raw;
    }

    /** Validates and burns the token; the same message for every failure mode. */
    @Transactional
    public User consume(String rawToken, OneTimeToken.Purpose purpose) {
        OneTimeToken token = tokenRepository.findByTokenHashAndPurpose(hash(rawToken), purpose)
                .orElseThrow(() -> new BusinessRuleException("Token is invalid or expired"));
        if (token.getUsedAt() != null || token.getExpiresAt().isBefore(Instant.now())) {
            throw new BusinessRuleException("Token is invalid or expired");
        }
        token.setUsedAt(Instant.now());
        tokenRepository.save(token);
        return token.getUser();
    }

    static String hash(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
