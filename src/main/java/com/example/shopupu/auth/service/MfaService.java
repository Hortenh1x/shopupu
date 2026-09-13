package com.example.shopupu.auth.service;

import com.example.shopupu.auth.dto.LoginResponse;
import com.example.shopupu.auth.entity.MfaChallenge;
import com.example.shopupu.auth.entity.MfaRecoveryCode;
import com.example.shopupu.auth.repository.MfaChallengeRepository;
import com.example.shopupu.auth.repository.MfaRecoveryCodeRepository;
import com.example.shopupu.common.audit.AuditService;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.config.MfaProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class MfaService {
    private final UserRepository users;
    private final MfaChallengeRepository challenges;
    private final MfaRecoveryCodeRepository recoveryCodes;
    private final MfaCrypto crypto;
    private final MfaProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final RefreshTokenService refreshTokens;
    private final AuthSessionService sessions;
    private final AuditService audit;
    public record Enrollment(String secret, String otpauthUri, Instant expiresAt) {}

    /** Called after first-factor verification while the account lock is held. */
    @Transactional
    public LoginResponse challenge(User user, String cart, String method) {
        crypto.requireAvailable();
        User locked = users.findByIdForUpdate(user.getId()).orElseThrow(MfaService::denied);
        challenges.invalidateForUser(locked.getId(), Instant.now());
        return create(locked, locked.getMfaSecretCiphertext() == null ? MfaChallenge.Kind.ENROLL : MfaChallenge.Kind.VERIFY,
                cart, method);
    }

    private LoginResponse create(User user, MfaChallenge.Kind kind, String cart, String method) {
        String raw = RefreshTokenService.randomToken();
        Instant expiry = Instant.now().plus(Duration.ofMinutes(5));
        challenges.save(MfaChallenge.builder().userId(user.getId()).tokenHash(OneTimeTokenService.hash(raw))
                .authVersion(user.getAuthVersion()).kind(kind).expiresAt(expiry)
                .pendingSecret(kind == MfaChallenge.Kind.ENROLL ? crypto.encrypt(crypto.newSecret(), user.getId()) : null)
                .guestCartToken(cart).loginMethod(method).build());
        return LoginResponse.challenge(kind == MfaChallenge.Kind.ENROLL ? "MFA_ENROLLMENT_REQUIRED" : "MFA_REQUIRED", raw, expiry);
    }

    @Transactional(propagation = Propagation.NEVER)
    public Enrollment startEnrollment(String token) {
        return withChallenge(token, MfaChallenge.Kind.ENROLL, (user, challenge) -> {
            String secret = crypto.decrypt(challenge.getPendingSecret(), user.getId());
            String issuer = properties.getIssuer();
            String uri = "otpauth://totp/" + encode(issuer + ":" + user.getEmail()) + "?secret=" + secret
                    + "&issuer=" + encode(issuer) + "&algorithm=SHA1&digits=6&period=30";
            return new Enrollment(secret, uri, challenge.getExpiresAt());
        });
    }

    @Transactional(propagation = Propagation.NEVER)
    public LoginResponse confirmEnrollment(String token, String code) {
        return withChallenge(token, MfaChallenge.Kind.ENROLL, (user, challenge) -> {
            String secret = crypto.decrypt(challenge.getPendingSecret(), user.getId());
            long step = crypto.acceptedStep(secret, code, Instant.now(), -1);
            if (step < 0) { fail(challenge); return null; }
            challenge.setUsedAt(Instant.now());
            user.setMfaSecretCiphertext(challenge.getPendingSecret());
            user.setMfaLastAcceptedStep(step);
            user.setAuthVersion(user.getAuthVersion() + 1);
            refreshTokens.revokeAll(user);
            recoveryCodes.deleteForUser(user.getId());
            var rawCodes = new ArrayList<String>();
            for (int i = 0; i < 10; i++) {
                String raw = crypto.recoveryCode(); rawCodes.add(raw);
                recoveryCodes.save(MfaRecoveryCode.builder().userId(user.getId())
                        .codeHash(OneTimeTokenService.hash(raw)).build());
            }
            LoginResponse pair = sessions.complete(user, Instant.now(), challenge.getGuestCartToken(), challenge.getLoginMethod());
            audit.record(user.getEmail(), "MFA_ENROLLED", "user", user.getId().toString(), "Existing sessions revoked");
            return new LoginResponse(pair.status(), pair.accessToken(), pair.refreshToken(), null, null, rawCodes);
        });
    }

    @Transactional(propagation = Propagation.NEVER)
    public LoginResponse verify(String token, String code, String recoveryCode) {
        return withChallenge(token, MfaChallenge.Kind.VERIFY, (user, challenge) -> {
            boolean hasCode = code != null && !code.isBlank();
            boolean hasRecovery = recoveryCode != null && !recoveryCode.isBlank();
            if (hasCode == hasRecovery) { fail(challenge); return null; }
            if (hasRecovery) {
                if (recoveryCodes.consume(user.getId(), OneTimeTokenService.hash(recoveryCode), Instant.now()) != 1) {
                    fail(challenge); return null;
                }
                challenge.setUsedAt(Instant.now());
                // First factor + single-use recovery only authorizes replacing the factor, never a full session.
                audit.record(user.getEmail(), "MFA_RECOVERY_STARTED", "user", user.getId().toString(), null);
                return create(user, MfaChallenge.Kind.ENROLL, challenge.getGuestCartToken(), challenge.getLoginMethod());
            }
            long step = crypto.acceptedStep(crypto.decrypt(user.getMfaSecretCiphertext(), user.getId()),
                    code, Instant.now(), user.getMfaLastAcceptedStep());
            if (step < 0) { fail(challenge); return null; }
            user.setMfaLastAcceptedStep(step);
            challenge.setUsedAt(Instant.now());
            return sessions.complete(user, Instant.now(), challenge.getGuestCartToken(), challenge.getLoginMethod());
        });
    }

    private <T> T withChallenge(String raw, MfaChallenge.Kind kind, BiFunction<User, MfaChallenge, T> action) {
        crypto.requireAvailable();
        String hash = OneTimeTokenService.hash(raw);
        Long id = challenges.findUserId(hash).orElseThrow(MfaService::denied);
        T result = new TransactionTemplate(transactionManager).execute(tx -> {
            User user = users.findByIdForUpdate(id).orElseThrow(MfaService::denied);
            MfaChallenge challenge = challenges.findByTokenHash(hash).orElseThrow(MfaService::denied);
            if (!user.isEnabled() || !RefreshTokenService.privileged(user) || challenge.getKind() != kind
                    || challenge.getUsedAt() != null || challenge.getAttempts() >= 5
                    || challenge.getAuthVersion() != user.getAuthVersion()
                    || !challenge.getExpiresAt().isAfter(Instant.now())) return null;
            return action.apply(user, challenge);
        });
        if (result == null) throw denied();
        return result;
    }
    private static void fail(MfaChallenge challenge) {
        challenge.setAttempts(challenge.getAttempts() + 1);
        if (challenge.getAttempts() >= 5) challenge.setUsedAt(Instant.now());
    }
    private static UnauthorizedException denied() { return new UnauthorizedException("MFA challenge or code is invalid or expired"); }
    private static String encode(String text) { return URLEncoder.encode(text, StandardCharsets.UTF_8).replace("+", "%20"); }
}
