package com.example.shopupu.auth.service;

import com.example.shopupu.auth.dto.LoginResponse;
import com.example.shopupu.auth.entity.OneTimeToken;
import com.example.shopupu.common.audit.AuditService;
import com.example.shopupu.common.exception.BadRequestException;
import com.example.shopupu.common.exception.ServiceUnavailableException;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.notifications.AccountNotificationEvent;
import com.example.shopupu.notifications.NotificationService;
import com.example.shopupu.security.JwtTokenProvider;
import com.example.shopupu.security.ShopUserDetails;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final UserRepository users;
    private final AuditService auditService;
    private final com.example.shopupu.cart.service.CartService cartService;
    private final OneTimeTokenService oneTimeTokenService;
    private final NotificationService notificationService;
    private final ObjectProvider<GoogleIdTokenVerifier> googleVerifierProvider;
    private final PlatformTransactionManager transactionManager;
    private final AuthSessionService sessions;
    private final MfaService mfa;
    private final PasswordPolicy passwords;
    private final AccountAuthThrottle throttle;
    private final ApplicationEventPublisher events;
    public record TokenPair(String accessToken, String refreshToken) {}

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public LoginResponse login(String email, String password) { return login(email, password, null); }
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public LoginResponse login(String email, String password, String guestCartToken) {
        String account = normalize(email);
        throttle.check("login", account);
        try {
            return new TransactionTemplate(transactionManager).execute(tx -> {
                Long userId = users.findIdByEmail(account).orElse(null);
                User user = userId == null ? null : users.findByIdForUpdate(userId).orElse(null);
                try { authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(account, password)); }
                catch (AuthenticationException ex) {
                    throw new UnauthorizedException("Wrong login or password");
                }
                if (user == null || !user.isEnabled()) throw new UnauthorizedException("Wrong login or password");
                return finishFirstFactor(user, guestCartToken, "LOCAL");
            });
        } catch (UnauthorizedException denied) {
            // The user lock and connection are released. A late failed attempt cannot recreate an erased email.
            auditService.record(AuditService.accountActor(account), "LOGIN_FAILED", "user", null, null);
            throw denied;
        }
    }

    /** Google signature/key HTTP verification runs before opening any database transaction. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NEVER)
    public LoginResponse loginWithGoogle(String token, String guestCartToken) {
        GoogleIdTokenVerifier verifier = googleVerifierProvider.getIfAvailable();
        if (verifier == null) throw new BadRequestException("Google login is not configured");
        GoogleIdToken idToken;
        try { idToken = verifier.verify(token); }
        catch (Exception ex) { throw new UnauthorizedException("Could not verify Google token"); }
        if (idToken == null) throw new UnauthorizedException("Invalid Google token");
        String email = idToken.getPayload().getEmail();
        if (email == null || !Boolean.TRUE.equals(idToken.getPayload().getEmailVerified()))
            throw new UnauthorizedException("Google account email is not verified");
        String account = normalize(email);
        throttle.check("login", account);
        Long id = userService.findOrCreateGoogleUser(account).getId();
        return new TransactionTemplate(transactionManager).execute(tx -> {
            User user = users.findByIdForUpdate(id).orElseThrow(() -> new UnauthorizedException("Invalid account"));
            if (!user.isEnabled()) throw new UnauthorizedException("Account is disabled");
            user.setEmailVerified(true);
            return finishFirstFactor(user, guestCartToken, "GOOGLE");
        });
    }
    private LoginResponse finishFirstFactor(User user, String cart, String method) {
        return RefreshTokenService.privileged(user) ? mfa.challenge(user, cart, method) : sessions.complete(user, null, cart, method);
    }
    @Transactional
    public TokenPair issueTokens(User user) {
        var response = sessions.issue(user, null);
        return new TokenPair(response.accessToken(), response.refreshToken());
    }
    public TokenPair refresh(String token) {
        var rotated = refreshTokenService.rotate(token);
        return new TokenPair(jwtTokenProvider.generateToken(new ShopUserDetails(rotated.entity().getUser()),
                rotated.entity().getMfaVerifiedAt()), rotated.rawToken());
    }
    @Transactional
    public void adoptGuestCart(String token, String email) { cartService.mergeGuestCart(token, email); }

    /** Registration remains usable with mail disabled; it does not mint an undeliverable token. */
    @Transactional
    public void sendEmailVerification(User user) {
        if (!notificationService.isAvailable() || user.isEmailVerified()) return;
        publishVerification(user);
    }
    @Transactional
    public void resendEmailVerification(User user) {
        requireEmail();
        throttle.check("resend", user.getEmail());
        if (!user.isEmailVerified()) publishVerification(user);
    }
    private void publishVerification(User user) {
        String raw = oneTimeTokenService.mint(user, OneTimeToken.Purpose.EMAIL_VERIFICATION);
        events.publishEvent(new AccountNotificationEvent(AccountNotificationEvent.Kind.EMAIL_VERIFICATION, user.getEmail(), raw));
    }
    @Transactional
    public void verifyEmail(String token) {
        User user = oneTimeTokenService.consume(token, OneTimeToken.Purpose.EMAIL_VERIFICATION);
        userService.markEmailVerified(user);
        auditService.record(user.getEmail(), "EMAIL_VERIFIED", "user", user.getId().toString(), null);
    }
    @Transactional
    public void forgotPassword(String email) {
        requireEmail();
        String account = normalize(email);
        throttle.check("reset", account);
        users.findIdByEmail(account).ifPresent(id -> {
            User user = users.findByIdForUpdate(id).orElse(null);
            if (user == null || !user.isEnabled()) return;
            String raw = oneTimeTokenService.mint(user, OneTimeToken.Purpose.PASSWORD_RESET);
            events.publishEvent(new AccountNotificationEvent(AccountNotificationEvent.Kind.PASSWORD_RESET, user.getEmail(), raw));
            auditService.record(account, "PASSWORD_RESET_REQUESTED", "user", user.getId().toString(), null);
        });
    }
    @Transactional
    public void resetPassword(String token, String newPassword) {
        passwords.validate(newPassword); // policy failure must not consume a valid token
        User user = oneTimeTokenService.consume(token, OneTimeToken.Purpose.PASSWORD_RESET);
        userService.setPassword(user, newPassword);
        refreshTokenService.revokeAll(user);
        auditService.record(user.getEmail(), "PASSWORD_RESET_COMPLETED", "user", user.getId().toString(), "All sessions revoked");
    }
    public void logout(String token, String email) { refreshTokenService.logout(token, email); }
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        passwords.validate(newPassword);
        User user = userService.changePassword(email, currentPassword, newPassword);
        oneTimeTokenService.invalidatePasswordResets(user);
        refreshTokenService.revokeAll(user);
        auditService.record(email, "PASSWORD_CHANGED", "user", user.getId().toString(), "All sessions revoked");
    }
    private void requireEmail() {
        if (!notificationService.isAvailable()) throw new ServiceUnavailableException("EMAIL_DELIVERY_UNAVAILABLE",
                "Email delivery is unavailable in this demo");
    }
    private static String normalize(String email) { return email.trim().toLowerCase(Locale.ROOT); }
}
