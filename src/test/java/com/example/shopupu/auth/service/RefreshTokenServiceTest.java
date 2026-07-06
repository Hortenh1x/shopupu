package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.auth.repository.RefreshTokenRepository;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.config.JwtProperties;
import com.example.shopupu.identity.entity.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    private User user;

    @BeforeEach
    void setUp() {
        JwtProperties props = new JwtProperties();
        props.setSecret("MySuperLongSecretKeyThatIsDefinitelySecure1234567890");
        props.setRefreshTokenTtlDays(7);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, props);
        user = User.builder().id(1L).email("user@example.com").passwordHash("hash").build();
    }

    @Test
    void mintStoresHashNotRawToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var minted = refreshTokenService.mint(user);

        assertSame(user, minted.entity().getUser());
        assertNotNull(minted.rawToken());
        // raw token must never equal the persisted value
        assertNotEquals(minted.rawToken(), minted.entity().getToken());
        assertEquals(RefreshTokenService.hash(minted.rawToken()), minted.entity().getToken());
        assertFalse(minted.entity().isRevoked());
        assertTrue(minted.entity().getExpiresAt().isAfter(Instant.now()));
    }

    @Test
    void verifyActiveReturnsTokenWhenValid() {
        RefreshToken token = token(false, Instant.now().plusSeconds(60));
        when(refreshTokenRepository.findByToken(RefreshTokenService.hash("refresh"))).thenReturn(Optional.of(token));

        assertSame(token, refreshTokenService.verifyActive("refresh"));
    }

    @Test
    void verifyActiveRejectsMissingAndExpiredTokens() {
        when(refreshTokenRepository.findByToken(RefreshTokenService.hash("missing"))).thenReturn(Optional.empty());
        assertThrows(UnauthorizedException.class, () -> refreshTokenService.verifyActive("missing"));

        when(refreshTokenRepository.findByToken(RefreshTokenService.hash("expired")))
                .thenReturn(Optional.of(token(false, Instant.now().minusSeconds(60))));
        assertThrows(UnauthorizedException.class, () -> refreshTokenService.verifyActive("expired"));
    }

    @Test
    void reusedRevokedTokenRevokesWholeChain() {
        RefreshToken revoked = token(true, Instant.now().plusSeconds(60));
        when(refreshTokenRepository.findByToken(RefreshTokenService.hash("stolen"))).thenReturn(Optional.of(revoked));

        assertThrows(UnauthorizedException.class, () -> refreshTokenService.verifyActive("stolen"));

        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void rotateRevokesOldTokenAndCreatesNewToken() {
        RefreshToken oldToken = token(false, Instant.now().plusSeconds(60));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var newToken = refreshTokenService.rotate(oldToken);

        assertTrue(oldToken.isRevoked());
        assertNotNull(newToken.rawToken());
        verify(refreshTokenRepository).save(oldToken);
    }

    @Test
    void revokeAllRevokesTokensForUser() {
        refreshTokenService.revokeAll(user);

        verify(refreshTokenRepository).revokeAllByUser(user);
    }

    @Test
    void logoutRevokesKnownTokenAndIgnoresUnknown() {
        RefreshToken token = token(false, Instant.now().plusSeconds(60));
        when(refreshTokenRepository.findByToken(RefreshTokenService.hash("known"))).thenReturn(Optional.of(token));
        when(refreshTokenRepository.findByToken(RefreshTokenService.hash("unknown"))).thenReturn(Optional.empty());
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        refreshTokenService.logout("known");
        refreshTokenService.logout("unknown");

        assertTrue(token.isRevoked());
    }

    private RefreshToken token(boolean revoked, Instant expiresAt) {
        return RefreshToken.builder()
                .id(1L)
                .user(user)
                .token("stored-hash")
                .revoked(revoked)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
    }
}
