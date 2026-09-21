package com.example.shopupu.auth.service;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.auth.repository.RefreshTokenRepository;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.config.JwtProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** All session mutations take the user lock before touching any refresh row. */
@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtProperties jwtProperties;
    private final PlatformTransactionManager transactionManager;
    private final UserRepository userRepository;
    private static final SecureRandom RANDOM = new SecureRandom();

    public record MintedToken(RefreshToken entity, String rawToken) {}

    @Transactional
    public MintedToken mint(User user) { return mint(user, null); }

    @Transactional
    public MintedToken mint(User user, Instant mfaVerifiedAt) {
        User locked = lock(user.getId());
        if (!locked.isEnabled() || !assuranceValid(locked, mfaVerifiedAt)) {
            throw new UnauthorizedException("Sign in with MFA to continue");
        }
        touchLastLogin(locked);
        String raw = randomToken();
        var token = RefreshToken.builder().user(locked).token(hash(raw))
                .authVersion(locked.getAuthVersion()).mfaVerifiedAt(mfaVerifiedAt)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(jwtProperties.getRefreshTokenTtlDays(), ChronoUnit.DAYS))
                .revoked(false).build();
        return new MintedToken(refreshTokenRepository.save(token), raw);
    }

    /** A denied outcome commits reuse revocation before the 401 is thrown, without a nested transaction. */
    @Transactional(propagation = Propagation.NEVER)
    public MintedToken rotate(String rawToken) {
        String hash = hash(rawToken);
        Long userId = refreshTokenRepository.findUserIdByToken(hash)
                .orElseThrow(() -> new UnauthorizedException("Invalid refresh token"));
        MintedToken result = new TransactionTemplate(transactionManager).execute(tx -> {
            User user = lock(userId);
            RefreshToken old = refreshTokenRepository.findByToken(hash).orElse(null);
            if (old == null) return null;
            if (old.isRevoked()) {
                refreshTokenRepository.revokeAllByUser(user);
                user.setAuthVersion(user.getAuthVersion() + 1);
                return null;
            }
            if (!user.isEnabled() || old.getAuthVersion() != user.getAuthVersion()
                    || !assuranceValid(user, old.getMfaVerifiedAt())) return null;
            if (refreshTokenRepository.consume(old.getId(), Instant.now()) != 1) return null;
            return mint(user, old.getMfaVerifiedAt());
        });
        if (result == null) throw new UnauthorizedException("Invalid refresh token");
        return result;
    }

    @Transactional
    public void revokeAll(User user) { refreshTokenRepository.revokeAllByUser(lock(user.getId())); }

    /** Only the authenticated owner can revoke this token; unknown/foreign tokens remain idempotent. */
    @Transactional
    public void logout(String rawToken, String ownerEmail) {
        String hash = hash(rawToken);
        refreshTokenRepository.findUserIdByToken(hash).ifPresent(id -> {
            User user = lock(id);
            if (!user.getEmail().equals(ownerEmail)) return;
            refreshTokenRepository.findByToken(hash).ifPresent(token -> token.setRevoked(true));
        });
    }

    public static boolean privileged(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getName().equals("ADMIN") || r.getName().equals("MANAGER"));
    }

    public static boolean assuranceValid(User user, Instant verifiedAt) {
        return !privileged(user) || (user.getMfaSecretCiphertext() != null && verifiedAt != null
                && !verifiedAt.isAfter(Instant.now()) && verifiedAt.plus(Duration.ofHours(12)).isAfter(Instant.now()));
    }

    /**
     * Every issued session (sign-in, registration, renewal) counts as activity for the
     * inactivity retention rule; renewals are frequent, so the row is only written hourly.
     */
    private static void touchLastLogin(User user) {
        Instant now = Instant.now();
        if (user.getLastLoginAt() == null || user.getLastLoginAt().isBefore(now.minus(1, ChronoUnit.HOURS))) {
            user.setLastLoginAt(now);
        }
    }

    private User lock(Long id) {
        return userRepository.findByIdForUpdate(id).orElseThrow(() -> new UnauthorizedException("Invalid account"));
    }
    static String randomToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    static String hash(String raw) {
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable", ex); }
    }
}
