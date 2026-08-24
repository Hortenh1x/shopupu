package com.example.shopupu.auth.service;

import com.example.shopupu.common.exception.BadRequestException;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.security.JwtTokenProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final com.example.shopupu.common.audit.AuditService auditService;
    private final com.example.shopupu.cart.service.CartService cartService;
    private final OneTimeTokenService oneTimeTokenService;
    private final com.example.shopupu.notifications.NotificationService notificationService;
    private final ObjectProvider<GoogleIdTokenVerifier> googleVerifierProvider;

    public record TokenPair(String accessToken, String refreshToken) {}

    @Transactional
    public TokenPair login(String email, String password) {
        return login(email, password, null);
    }

    @Transactional
    public TokenPair login(String email, String password, String guestCartToken) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (AuthenticationException e) {
            auditService.record(email, "LOGIN_FAILED", "user", null, null);
            // Uniform message: no hint whether the account exists (SEC-16).
            throw new UnauthorizedException("Wrong login or password");
        }
        User user = userService.getByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Wrong login or password"));
        auditService.record(email, "LOGIN_SUCCEEDED", "user", String.valueOf(user.getId()), null);
        cartService.mergeGuestCart(guestCartToken, email);
        return issueTokens(user);
    }

    /**
     * Logs in (or provisions) a user from a Google ID token (AUTH-13). The token
     * is verified against Google's keys (signature, issuer, audience, expiry);
     * we additionally require {@code email_verified} before trusting the email.
     * Inert until {@code google.client-id} is configured.
     */
    @Transactional
    public TokenPair loginWithGoogle(String idTokenString, String guestCartToken) {
        GoogleIdTokenVerifier verifier = googleVerifierProvider.getIfAvailable();
        if (verifier == null) {
            throw new BadRequestException("Google login is not configured");
        }

        GoogleIdToken idToken;
        try {
            idToken = verifier.verify(idTokenString);
        } catch (Exception e) {
            throw new UnauthorizedException("Could not verify Google token");
        }
        if (idToken == null) {
            throw new UnauthorizedException("Invalid Google token");
        }

        GoogleIdToken.Payload payload = idToken.getPayload();
        String email = payload.getEmail();
        if (email == null || !Boolean.TRUE.equals(payload.getEmailVerified())) {
            throw new UnauthorizedException("Google account email is not verified");
        }

        User user = userService.findOrCreateGoogleUser(email);
        if (!user.isEnabled()) {
            throw new UnauthorizedException("Account is disabled");
        }
        auditService.record(email, "LOGIN_SUCCEEDED_GOOGLE", "user", String.valueOf(user.getId()), null);
        cartService.mergeGuestCart(guestCartToken, email);
        return issueTokens(user);
    }

    @Transactional
    public TokenPair issueTokens(User user) {
        UserDetails principal = toPrincipal(user);

        String access = jwtTokenProvider.generateToken(principal);
        var refresh = refreshTokenService.mint(user);

        return new TokenPair(access, refresh.rawToken());
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        var oldToken = refreshTokenService.verifyActive(refreshToken);
        var user = oldToken.getUser();
        var newRt = refreshTokenService.rotate(oldToken);

        UserDetails principal = toPrincipal(user);

        String newAccess = jwtTokenProvider.generateToken(principal);
        return new TokenPair(newAccess, newRt.rawToken());
    }

    /** Carries an anonymous cart over to a freshly registered account (CART-02). */
    @Transactional
    public void adoptGuestCart(String guestCartToken, String email) {
        cartService.mergeGuestCart(guestCartToken, email);
    }

    /** Issues and sends an email-verification token (AUTH-06). */
    @Transactional
    public void sendEmailVerification(User user) {
        String token = oneTimeTokenService.mint(user,
                com.example.shopupu.auth.entity.OneTimeToken.Purpose.EMAIL_VERIFICATION);
        notificationService.sendEmailVerification(user.getEmail(), token);
    }

    /** Confirms the email behind a one-time token. */
    @Transactional
    public void verifyEmail(String token) {
        User user = oneTimeTokenService.consume(token,
                com.example.shopupu.auth.entity.OneTimeToken.Purpose.EMAIL_VERIFICATION);
        userService.markEmailVerified(user);
        auditService.record(user.getEmail(), "EMAIL_VERIFIED", "user",
                String.valueOf(user.getId()), null);
    }

    /**
     * Starts a password reset (AUTH-07). Always silent about whether the
     * account exists (SEC-16): unknown emails produce no observable difference.
     */
    @Transactional
    public void forgotPassword(String email) {
        userService.getByEmail(email).ifPresent(user -> {
            String token = oneTimeTokenService.mint(user,
                    com.example.shopupu.auth.entity.OneTimeToken.Purpose.PASSWORD_RESET);
            notificationService.sendPasswordReset(user.getEmail(), token);
            auditService.record(email, "PASSWORD_RESET_REQUESTED", "user",
                    String.valueOf(user.getId()), null);
        });
    }

    /** Completes a password reset: one-shot token, all sessions revoked. */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        User user = oneTimeTokenService.consume(token,
                com.example.shopupu.auth.entity.OneTimeToken.Purpose.PASSWORD_RESET);
        userService.setPassword(user, newPassword);
        refreshTokenService.revokeAll(user);
        auditService.record(user.getEmail(), "PASSWORD_RESET_COMPLETED", "user",
                String.valueOf(user.getId()), "All sessions revoked");
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.logout(refreshToken);
    }

    /** Changes the password and invalidates every active session of the user (AUTH-12). */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userService.changePassword(email, currentPassword, newPassword);
        refreshTokenService.revokeAll(user);
        auditService.record(email, "PASSWORD_CHANGED", "user", String.valueOf(user.getId()),
                "All sessions revoked");
    }

    private UserDetails toPrincipal(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .collect(Collectors.toSet());
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }
}
