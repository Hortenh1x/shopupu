package com.example.shopupu.auth.service;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.identity.entity.Role;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * describes the AuthServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @InjectMocks
    private AuthService authService;

    // handles issueTokens.
    @Test
    void issueTokensCreatesAccessAndRefreshTokens() {
        User user = user();
        RefreshToken refreshToken = refreshToken(user, "refresh-1");
        when(jwtTokenProvider.generateToken(any())).thenReturn("access-1");
        when(refreshTokenService.mint(user)).thenReturn(refreshToken);

        var pair = authService.issueTokens(user);

        assertEquals("access-1", pair.accessToken());
        assertEquals("refresh-1", pair.refreshToken());
        verify(refreshTokenService).mint(user);
    }

    // handles refresh.
    @Test
    void refreshRotatesRefreshTokenAndIssuesNewAccessToken() {
        User user = user();
        RefreshToken oldToken = refreshToken(user, "old-refresh");
        RefreshToken newToken = refreshToken(user, "new-refresh");
        when(refreshTokenService.verifyActive("old-refresh")).thenReturn(oldToken);
        when(refreshTokenService.rotate(oldToken)).thenReturn(newToken);
        when(jwtTokenProvider.generateToken(any())).thenReturn("new-access");

        var pair = authService.refresh("old-refresh");

        assertEquals("new-access", pair.accessToken());
        assertEquals("new-refresh", pair.refreshToken());
        verify(refreshTokenService).rotate(oldToken);
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
