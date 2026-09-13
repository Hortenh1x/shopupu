package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.shopupu.auth.dto.LoginResponse;
import com.example.shopupu.auth.entity.OneTimeToken;
import com.example.shopupu.common.exception.*;
import com.example.shopupu.identity.entity.Role;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.notifications.AccountNotificationEvent;
import com.example.shopupu.notifications.NotificationService;
import com.example.shopupu.security.JwtTokenProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock RefreshTokenService refreshTokenService;
    @Mock AuthenticationManager authenticationManager;
    @Mock UserService userService;
    @Mock UserRepository users;
    @Mock com.example.shopupu.common.audit.AuditService auditService;
    @Mock com.example.shopupu.cart.service.CartService cartService;
    @Mock OneTimeTokenService oneTimeTokenService;
    @Mock NotificationService notificationService;
    @Mock ObjectProvider<GoogleIdTokenVerifier> googleVerifierProvider;
    @Mock GoogleIdTokenVerifier verifier;
    @Mock PlatformTransactionManager transactionManager;
    @Mock AuthSessionService sessions;
    @Mock MfaService mfa;
    @Spy PasswordPolicy passwords = new PasswordPolicy();
    @Mock AccountAuthThrottle throttle;
    @Mock ApplicationEventPublisher events;
    @InjectMocks AuthService auth;
    private void transaction(User user) {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
    }
    @Test void privilegedPasswordLoginReturnsChallengeWithoutIssuingTokensOrMergingCart() {
        User user = user("ADMIN"); transaction(user);
        when(users.findIdByEmail(user.getEmail())).thenReturn(Optional.of(user.getId()));
        when(mfa.challenge(user, "cart", "LOCAL")).thenReturn(LoginResponse.challenge("MFA_REQUIRED", "challenge", Instant.now().plusSeconds(300)));
        assertEquals("MFA_REQUIRED", auth.login(user.getEmail(), "old password", "cart").status());
        verifyNoInteractions(sessions, refreshTokenService, cartService);
    }
    @Test void badCredentialsHaveUniformMessage() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(authenticationManager.authenticate(any())).thenThrow(new BadCredentialsException("bad"));
        assertEquals("Wrong login or password", assertThrows(UnauthorizedException.class,
                () -> auth.login("unknown@example.com", "wrong")).getMessage());
        var order = inOrder(transactionManager, auditService);
        order.verify(transactionManager).rollback(any());
        order.verify(auditService).record(com.example.shopupu.common.audit.AuditService.accountActor("unknown@example.com"),
                "LOGIN_FAILED", "user", null, null);
    }
    @Test void disabledEmailFailsUniformlyWithoutLookupOrTokenMint() {
        for (String email : new String[]{"known@example.com", "unknown@example.com"})
            assertEquals("EMAIL_DELIVERY_UNAVAILABLE", assertThrows(ServiceUnavailableException.class, () -> auth.forgotPassword(email)).getCode());
        verifyNoInteractions(users, oneTimeTokenService, events);
    }
    @Test void registrationWithDisabledEmailDoesNotMintAnything() {
        auth.sendEmailVerification(user("CUSTOMER")); verifyNoInteractions(oneTimeTokenService, events);
    }
    @Test void enabledResetPublishesPostCommitEventInsteadOfCallingSender() {
        User user = user("CUSTOMER");
        when(notificationService.isAvailable()).thenReturn(true);
        when(users.findIdByEmail(user.getEmail())).thenReturn(Optional.of(user.getId()));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(oneTimeTokenService.mint(user, OneTimeToken.Purpose.PASSWORD_RESET)).thenReturn("secret");
        auth.forgotPassword(user.getEmail());
        verify(events).publishEvent(new AccountNotificationEvent(AccountNotificationEvent.Kind.PASSWORD_RESET, user.getEmail(), "secret"));
        verify(notificationService, never()).sendPasswordReset(any(), any());
    }
    @Test void invalidNewPasswordDoesNotConsumeToken() {
        assertThrows(BadRequestException.class, () -> auth.resetPassword("valid", "passwordpassword"));
        verifyNoInteractions(oneTimeTokenService, userService, refreshTokenService);
    }
    @Test void googleHttpVerificationPrecedesDatabaseTransactionAndAlsoRequiresMfa() throws Exception {
        User user = user("MANAGER"); transaction(user);
        var payload = new GoogleIdToken.Payload().setEmail(user.getEmail()).setEmailVerified(true);
        when(googleVerifierProvider.getIfAvailable()).thenReturn(verifier);
        when(verifier.verify("id-token")).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            verify(transactionManager, never()).getTransaction(any());
            return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
        });
        when(userService.findOrCreateGoogleUser(user.getEmail())).thenReturn(user);
        when(mfa.challenge(user, null, "GOOGLE")).thenReturn(LoginResponse.challenge("MFA_REQUIRED", "challenge", Instant.now()));
        assertEquals("MFA_REQUIRED", auth.loginWithGoogle("id-token", null).status()); verifyNoInteractions(sessions);
    }
    @Test void googleRejectsMissingConfigurationInvalidAndUnverifiedTokens() throws Exception {
        assertThrows(BadRequestException.class, () -> auth.loginWithGoogle("any", null));
        when(googleVerifierProvider.getIfAvailable()).thenReturn(verifier);
        assertThrows(UnauthorizedException.class, () -> auth.loginWithGoogle("forged", null));
        var payload = new GoogleIdToken.Payload().setEmail("user@example.com").setEmailVerified(false);
        when(verifier.verify("unverified")).thenReturn(new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]));
        assertThrows(UnauthorizedException.class, () -> auth.loginWithGoogle("unverified", null)); verifyNoInteractions(users, sessions);
    }
    private User user(String role) { return User.builder().id(1L).email("user@example.com").passwordHash("hash")
            .roles(Set.of(Role.builder().name(role).build())).build(); }
}
