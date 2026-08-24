package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.common.exception.BadRequestException;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.identity.entity.AuthProvider;
import com.example.shopupu.identity.entity.Role;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.security.JwtTokenProvider;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private UserService userService;

    @Mock
    private com.example.shopupu.common.audit.AuditService auditService;

    @Mock
    private com.example.shopupu.cart.service.CartService cartService;

    @Mock
    private OneTimeTokenService oneTimeTokenService;

    @Mock
    private com.example.shopupu.notifications.NotificationService notificationService;

    @Mock
    private ObjectProvider<GoogleIdTokenVerifier> googleVerifierProvider;

    @Mock
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @InjectMocks
    private AuthService authService;

    @Test
    void issueTokensCreatesAccessAndRefreshTokens() {
        User user = user();
        var minted = new RefreshTokenService.MintedToken(refreshToken(user, "hash-1"), "refresh-1");
        when(jwtTokenProvider.generateToken(any())).thenReturn("access-1");
        when(refreshTokenService.mint(user)).thenReturn(minted);

        var pair = authService.issueTokens(user);

        assertEquals("access-1", pair.accessToken());
        assertEquals("refresh-1", pair.refreshToken());
        verify(refreshTokenService).mint(user);
    }

    @Test
    void refreshRotatesRefreshTokenAndIssuesNewAccessToken() {
        User user = user();
        RefreshToken oldToken = refreshToken(user, "old-hash");
        var newMinted = new RefreshTokenService.MintedToken(refreshToken(user, "new-hash"), "new-refresh");
        when(refreshTokenService.verifyActive("old-refresh")).thenReturn(oldToken);
        when(refreshTokenService.rotate(oldToken)).thenReturn(newMinted);
        when(jwtTokenProvider.generateToken(any())).thenReturn("new-access");

        var pair = authService.refresh("old-refresh");

        assertEquals("new-access", pair.accessToken());
        assertEquals("new-refresh", pair.refreshToken());
        verify(refreshTokenService).rotate(oldToken);
    }

    @Test
    void loginIssuesTokensForValidCredentials() {
        User user = user();
        var minted = new RefreshTokenService.MintedToken(refreshToken(user, "hash-1"), "refresh-1");
        when(userService.getByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateToken(any())).thenReturn("access-1");
        when(refreshTokenService.mint(user)).thenReturn(minted);

        var pair = authService.login("user@example.com", "password");

        assertEquals("access-1", pair.accessToken());
        verify(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));
    }

    @Test
    void loginRejectsBadCredentialsWithUniformMessage() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad"));

        var ex = assertThrows(UnauthorizedException.class,
                () -> authService.login("user@example.com", "wrong"));
        assertEquals("Wrong login or password", ex.getMessage());
    }

    @Test
    void forgotPasswordIsSilentForUnknownEmail() {
        when(userService.getByEmail("ghost@example.com")).thenReturn(Optional.empty());

        authService.forgotPassword("ghost@example.com");

        org.mockito.Mockito.verifyNoInteractions(oneTimeTokenService, notificationService);
    }

    @Test
    void forgotPasswordSendsTokenForKnownEmail() {
        User user = user();
        when(userService.getByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(oneTimeTokenService.mint(user, com.example.shopupu.auth.entity.OneTimeToken.Purpose.PASSWORD_RESET))
                .thenReturn("raw-token");

        authService.forgotPassword("user@example.com");

        verify(notificationService).sendPasswordReset("user@example.com", "raw-token");
    }

    @Test
    void resetPasswordConsumesTokenAndRevokesSessions() {
        User user = user();
        when(oneTimeTokenService.consume("raw-token",
                com.example.shopupu.auth.entity.OneTimeToken.Purpose.PASSWORD_RESET)).thenReturn(user);

        authService.resetPassword("raw-token", "newPassword1");

        verify(userService).setPassword(user, "newPassword1");
        verify(refreshTokenService).revokeAll(user);
    }

    @Test
    void changePasswordRevokesAllSessions() {
        User user = user();
        when(userService.changePassword("user@example.com", "old", "newPassword1")).thenReturn(user);

        authService.changePassword("user@example.com", "old", "newPassword1");

        verify(refreshTokenService).revokeAll(user);
    }

    @Test
    void googleLoginRejectsWhenNotConfigured() {
        when(googleVerifierProvider.getIfAvailable()).thenReturn(null);

        var ex = assertThrows(BadRequestException.class,
                () -> authService.loginWithGoogle("some-token", null));
        assertEquals("Google login is not configured", ex.getMessage());
    }

    @Test
    void googleLoginRejectsInvalidToken() throws Exception {
        when(googleVerifierProvider.getIfAvailable()).thenReturn(googleIdTokenVerifier);
        when(googleIdTokenVerifier.verify("forged")).thenReturn(null);

        assertThrows(UnauthorizedException.class,
                () -> authService.loginWithGoogle("forged", null));
        org.mockito.Mockito.verifyNoInteractions(userService, refreshTokenService);
    }

    @Test
    void googleLoginWrapsVerifierFailureAsUnauthorized() throws Exception {
        when(googleVerifierProvider.getIfAvailable()).thenReturn(googleIdTokenVerifier);
        when(googleIdTokenVerifier.verify("boom")).thenThrow(new java.io.IOException("keys unavailable"));

        var ex = assertThrows(UnauthorizedException.class,
                () -> authService.loginWithGoogle("boom", null));
        assertEquals("Could not verify Google token", ex.getMessage());
    }

    @Test
    void googleLoginRejectsUnverifiedGoogleEmail() throws Exception {
        when(googleVerifierProvider.getIfAvailable()).thenReturn(googleIdTokenVerifier);
        when(googleIdTokenVerifier.verify("unverified"))
                .thenReturn(googleIdToken("user@example.com", false));

        assertThrows(UnauthorizedException.class,
                () -> authService.loginWithGoogle("unverified", null));
        org.mockito.Mockito.verifyNoInteractions(userService, refreshTokenService);
    }

    @Test
    void googleLoginIssuesTokensAndMergesGuestCart() throws Exception {
        User user = user();
        when(googleVerifierProvider.getIfAvailable()).thenReturn(googleIdTokenVerifier);
        when(googleIdTokenVerifier.verify("good")).thenReturn(googleIdToken("user@example.com", true));
        when(userService.findOrCreateGoogleUser("user@example.com")).thenReturn(user);
        when(jwtTokenProvider.generateToken(any())).thenReturn("access-1");
        when(refreshTokenService.mint(user))
                .thenReturn(new RefreshTokenService.MintedToken(refreshToken(user, "hash-1"), "refresh-1"));

        var pair = authService.loginWithGoogle("good", "guest-cart-42");

        assertEquals("access-1", pair.accessToken());
        assertEquals("refresh-1", pair.refreshToken());
        verify(cartService).mergeGuestCart("guest-cart-42", "user@example.com");
        verify(auditService).record("user@example.com", "LOGIN_SUCCEEDED_GOOGLE", "user", "10", null);
    }

    @Test
    void googleLoginRejectsDisabledAccount() throws Exception {
        User disabled = User.builder()
                .id(10L)
                .email("user@example.com")
                .passwordHash("hash")
                .enabled(false)
                .roles(Set.of(Role.builder().id(1L).name("CUSTOMER").build()))
                .build();
        when(googleVerifierProvider.getIfAvailable()).thenReturn(googleIdTokenVerifier);
        when(googleIdTokenVerifier.verify("good")).thenReturn(googleIdToken("user@example.com", true));
        when(userService.findOrCreateGoogleUser("user@example.com")).thenReturn(disabled);

        var ex = assertThrows(UnauthorizedException.class,
                () -> authService.loginWithGoogle("good", null));
        assertEquals("Account is disabled", ex.getMessage());
        org.mockito.Mockito.verifyNoInteractions(refreshTokenService);
    }

    @Test
    void refreshWorksForGoogleProvisionedUser() {
        User googleUser = User.builder()
                .id(11L)
                .email("g-user@example.com")
                .passwordHash("random-hash")
                .enabled(true)
                .authProvider(AuthProvider.GOOGLE)
                .roles(Set.of(Role.builder().id(1L).name("CUSTOMER").build()))
                .build();
        RefreshToken oldToken = refreshToken(googleUser, "old-hash");
        var newMinted = new RefreshTokenService.MintedToken(refreshToken(googleUser, "new-hash"), "new-refresh");
        when(refreshTokenService.verifyActive("old-refresh")).thenReturn(oldToken);
        when(refreshTokenService.rotate(oldToken)).thenReturn(newMinted);
        when(jwtTokenProvider.generateToken(any())).thenReturn("new-access");

        var pair = authService.refresh("old-refresh");

        assertEquals("new-access", pair.accessToken());
        assertEquals("new-refresh", pair.refreshToken());
    }

    private GoogleIdToken googleIdToken(String email, boolean emailVerified) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
    }

    private User user() {
        Role role = Role.builder().id(1L).name("CUSTOMER").build();
        return User.builder()
                .id(10L)
                .email("user@example.com")
                .passwordHash("hash")
                .enabled(true)
                .roles(Set.of(role))
                .build();
    }

    private RefreshToken refreshToken(User user, String token) {
        return RefreshToken.builder()
                .id(1L)
                .user(user)
                .token(token)
                .expiresAt(Instant.now().plusSeconds(60))
                .createdAt(Instant.now())
                .revoked(false)
                .build();
    }
}
