package com.example.shopupu.auth.service;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.auth.repository.RefreshTokenRepository;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.config.JwtProperties;
import com.example.shopupu.identity.entity.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Opaque refresh tokens. Only a SHA-256 hash is persisted, so a leaked database
 * dump cannot be replayed. Reuse of a rotated (revoked) token revokes the whole
 * session family of that user (AUTH-03).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final PlatformTransactionManager transactionManager;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** The persisted entity plus the raw token value that is returned to the client once. */
    public record MintedToken(RefreshToken entity, String rawToken) {}

    @Transactional
    public MintedToken mint(User user) {
        String raw = generateRawToken();
        var token = RefreshToken.builder()
                .user(user)
                .token(hash(raw))
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshTokenTtlDays(), ChronoUnit.DAYS))
                .revoked(false)
                .build();
        return new MintedToken(refreshTokenRepository.save(token), raw);
    }

    @Transactional
    public RefreshToken verifyActive(String rawToken) {
        var rt = refreshTokenRepository.findByToken(hash(rawToken))
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));

        if (rt.isRevoked()) {
            // Reuse of a rotated token: assume the token leaked and kill the whole chain.
            // REQUIRES_NEW like the audit trail: the UnauthorizedException below rolls the
            // surrounding transaction back, and the revocation must survive that rollback.
            TransactionTemplate revokeTx = new TransactionTemplate(transactionManager);
            revokeTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            Integer revoked = revokeTx.execute(tx -> refreshTokenRepository.revokeAllByUser(rt.getUser()));
            log.warn("Refresh token reuse detected for user id={}, revoked {} active tokens",
                    rt.getUser().getId(), revoked);
            throw new UnauthorizedException("Invalid refresh token");
        }

        if (rt.getExpiresAt().isBefore(Instant.now())) {
            throw new UnauthorizedException("Refresh token is expired");
        }
        return rt;
    }

    @Transactional
    public MintedToken rotate(RefreshToken oldToken) {
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);
        return mint(oldToken.getUser());
    }

    @Transactional
    public void revokeAll(User user) {
        refreshTokenRepository.revokeAllByUser(user);
    }

    @Transactional
    public void revoke(RefreshToken rt) {
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);
    }

    /** Idempotent: unknown tokens are ignored so logout never fails. */
    @Transactional
    public void logout(String rawToken) {
        refreshTokenRepository.findByToken(hash(rawToken)).ifPresent(this::revoke);
    }

    private String generateRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
