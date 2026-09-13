package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.auth.entity.OneTimeToken;
import com.example.shopupu.auth.repository.MfaChallengeRepository;
import com.example.shopupu.auth.repository.OneTimeTokenRepository;
import com.example.shopupu.common.exception.BadRequestException;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.RoleRepository;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.security.JwtTokenProvider;
import com.example.shopupu.security.ShopUserDetails;
import com.example.shopupu.support.PostgresContainerSupport;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/** Real PostgreSQL locks/rollback/security state; no external identity or email calls. */
@SpringBootTest
@ActiveProfiles("test")
class AuthSessionIntegrityIT extends PostgresContainerSupport {
    @Autowired AuthService auth;
    @Autowired UserService userService;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired OneTimeTokenService oneTime;
    @Autowired OneTimeTokenRepository oneTimeRows;
    @Autowired MfaService mfa;
    @Autowired MfaCrypto crypto;
    @Autowired MfaChallengeRepository challenges;
    @Autowired JwtTokenProvider jwt;
    @Autowired JdbcClient jdbc;
    private static final String PASSWORD = "A quiet test meadow in 2026";

    @Test void concurrentRefreshHasOneWinnerAndReuseRevokesThatWinnerWithoutDeadlock() throws Exception {
        User user = customer(); var original = auth.issueTokens(user);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<AuthService.TokenPair> attempt = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { return auth.refresh(original.refreshToken()); }
                catch (UnauthorizedException denied) { return null; }
            };
            Future<AuthService.TokenPair> first = executor.submit(attempt), second = executor.submit(attempt);
            var a = first.get(15, TimeUnit.SECONDS); var b = second.get(15, TimeUnit.SECONDS);
            assertNotEquals(a == null, b == null, "exactly one rotation may mint a successor");
            var winner = a == null ? b : a;
            assertFalse(jwt.isTokenValid(winner.accessToken(), new ShopUserDetails(users.findById(user.getId()).orElseThrow())));
            assertEquals(0L, jdbc.sql("select count(*) from refresh_tokens where user_id = :id and revoked = false")
                    .param("id", user.getId()).query(Long.class).single());
        }
    }
    @Test void resetPolicyFailureLeavesTokenUsableThenRevokesAccessImmediately() {
        User user = customer(); var pair = auth.issueTokens(user);
        String raw = oneTime.mint(user, OneTimeToken.Purpose.PASSWORD_RESET);
        assertThrows(BadRequestException.class, () -> auth.resetPassword(raw, "passwordpassword"));
        auth.resetPassword(raw, "Another quiet meadow test 2026");
        assertFalse(jwt.isTokenValid(pair.accessToken(), new ShopUserDetails(users.findById(user.getId()).orElseThrow())));
        assertThrows(BusinessRuleException.class, () -> auth.resetPassword(raw, PASSWORD));
        assertThrows(UnauthorizedException.class, () -> auth.refresh(pair.refreshToken()));
    }
    @Test void passwordChangeAlsoInvalidatesAlreadyIssuedRecoveryLinks() {
        User user = customer(); var pair = auth.issueTokens(user);
        String reset = oneTime.mint(user, OneTimeToken.Purpose.PASSWORD_RESET);
        auth.changePassword(user.getEmail(), PASSWORD, "A newly chosen quiet meadow");
        assertFalse(jwt.isTokenValid(pair.accessToken(), new ShopUserDetails(users.findById(user.getId()).orElseThrow())));
        assertThrows(BusinessRuleException.class, () -> auth.resetPassword(reset, PASSWORD));
    }
    @Test void concurrentOneTimeResetAndMintRemainSingleUse() throws Exception {
        User user = customer(); String raw = oneTime.mint(user, OneTimeToken.Purpose.PASSWORD_RESET);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Boolean> attempt = () -> {
                barrier.await(5, TimeUnit.SECONDS);
                try { auth.resetPassword(raw, "Another quiet meadow test 2026"); return true; }
                catch (BusinessRuleException denied) { return false; }
            };
            Future<Boolean> first = executor.submit(attempt), second = executor.submit(attempt);
            assertNotEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            var mintBarrier = new CyclicBarrier(2);
            Callable<String> mint = () -> { mintBarrier.await(5, TimeUnit.SECONDS); return oneTime.mint(user, OneTimeToken.Purpose.EMAIL_VERIFICATION); };
            Future<String> one = executor.submit(mint), two = executor.submit(mint);
            one.get(15, TimeUnit.SECONDS); two.get(15, TimeUnit.SECONDS);
            assertEquals(1L, jdbc.sql("select count(*) from one_time_tokens where user_id = :id and purpose = 'EMAIL_VERIFICATION' and used_at is null")
                    .param("id", user.getId()).query(Long.class).single());
        }
    }
    @Test void wrongPurposeExpiredAndUnknownTokensAreDenied() {
        User user = customer(); String raw = oneTime.mint(user, OneTimeToken.Purpose.EMAIL_VERIFICATION);
        assertThrows(BusinessRuleException.class, () -> auth.resetPassword(raw, PASSWORD));
        assertThrows(BusinessRuleException.class, () -> auth.verifyEmail("unknown"));
        jdbc.sql("update one_time_tokens set expires_at = now() - interval '1 second' where user_id = :id")
                .param("id", user.getId()).update();
        assertThrows(BusinessRuleException.class, () -> auth.verifyEmail(raw));
    }
    @Test void newAdminRoleCannotUpgradeAnOldCustomerAccessOrRefreshToken() {
        User user = customer(); var pair = auth.issueTokens(user);
        promote(user);
        assertFalse(jwt.isTokenValid(pair.accessToken(), new ShopUserDetails(users.findById(user.getId()).orElseThrow())));
        assertThrows(UnauthorizedException.class, () -> auth.refresh(pair.refreshToken()));
        assertEquals("MFA_ENROLLMENT_REQUIRED", auth.login(user.getEmail(), PASSWORD).status());
    }
    @Test void privilegedEnrollmentAttemptsReplayAndRecoveryAreBoundToOneAccountAndVersion() {
        User user = customer(); promote(user);
        var challenge = auth.login(user.getEmail(), PASSWORD);
        assertNull(challenge.accessToken());
        var enrollment = mfa.startEnrollment(challenge.challengeToken());
        assertEquals(enrollment.secret(), mfa.startEnrollment(challenge.challengeToken()).secret());
        long currentStep = Instant.now().getEpochSecond() / 30;
        var enrolled = mfa.confirmEnrollment(challenge.challengeToken(), crypto.totp(enrollment.secret(), currentStep));
        assertEquals(10, enrolled.recoveryCodes().size());
        assertThrows(UnauthorizedException.class, () -> mfa.confirmEnrollment(challenge.challengeToken(), "000000"));
        User current = users.findById(user.getId()).orElseThrow();
        assertTrue(jwt.isTokenValid(enrolled.accessToken(), new ShopUserDetails(current)));
        assertNotEquals(enrollment.secret(), current.getMfaSecretCiphertext());
        var login = auth.login(user.getEmail(), PASSWORD);
        assertThrows(UnauthorizedException.class, () -> mfa.verify(login.challengeToken(), crypto.totp(enrollment.secret(), currentStep), null));
        var authenticated = mfa.verify(login.challengeToken(), crypto.totp(enrollment.secret(), currentStep + 1), null);
        assertEquals("AUTHENTICATED", authenticated.status());
        var recoveryLogin = auth.login(user.getEmail(), PASSWORD);
        var recovery = mfa.verify(recoveryLogin.challengeToken(), null, enrolled.recoveryCodes().getFirst());
        assertEquals("MFA_ENROLLMENT_REQUIRED", recovery.status()); assertNull(recovery.accessToken());
        assertEquals(current.getMfaSecretCiphertext(), users.findById(user.getId()).orElseThrow().getMfaSecretCiphertext());
        var replacement = mfa.startEnrollment(recovery.challengeToken());
        mfa.confirmEnrollment(recovery.challengeToken(), crypto.totp(replacement.secret(), Instant.now().getEpochSecond() / 30));
        assertFalse(jwt.isTokenValid(authenticated.accessToken(), new ShopUserDetails(users.findById(user.getId()).orElseThrow())));
        var reusedRecovery = auth.login(user.getEmail(), PASSWORD);
        assertThrows(UnauthorizedException.class, () -> mfa.verify(reusedRecovery.challengeToken(), null, enrolled.recoveryCodes().getFirst()));
    }
    @Test void fiveFailedMfaAttemptsAreCommittedAndPasswordResetInvalidatesOutstandingChallenge() {
        User user = customer(); promote(user); var challenge = auth.login(user.getEmail(), PASSWORD);
        for (int i = 0; i < 5; i++) assertThrows(UnauthorizedException.class, () -> mfa.confirmEnrollment(challenge.challengeToken(), "bad"));
        var row = challenges.findByTokenHash(OneTimeTokenService.hash(challenge.challengeToken())).orElseThrow();
        assertEquals(5, row.getAttempts()); assertNotNull(row.getUsedAt());
        var fresh = auth.login(user.getEmail(), PASSWORD);
        String reset = oneTime.mint(user, OneTimeToken.Purpose.PASSWORD_RESET);
        auth.resetPassword(reset, "A different quiet test meadow");
        assertThrows(UnauthorizedException.class, () -> mfa.startEnrollment(fresh.challengeToken()));
    }
    private User customer() { return userService.registerUser("identity-" + System.nanoTime() + "@example.com", PASSWORD); }
    private void promote(User user) {
        user.setRoles(Set.of(roles.findByName("ADMIN").orElseThrow())); users.saveAndFlush(user);
    }
}
