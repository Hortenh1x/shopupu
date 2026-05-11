package com.example.shopupu.auth.service;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.auth.repository.RefreshTokenRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * describes the RefreshTokenServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private RefreshTokenService refreshTokenService;

    private User user;

    // handles setUp.
    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository);
        ReflectionTestUtils.setField(refreshTokenService, "refreshDays", 7L);
        user = User.builder().id(1L).email("user@example.com").passwordHash("hash").build();
    }

    // handles mint.
    @Test
    void mintCreatesActiveRefreshToken() {
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken token = refreshTokenService.mint(user);

        assertSame(user, token.getUser());
        assertNotNull(token.getToken());
        assertFalse(token.isRevoked());
        assertTrue(token.getExpiresAt().isAfter(Instant.now()));
    }

    // handles verifyActive.
    @Test
    void verifyActiveReturnsTokenWhenValid() {
        RefreshToken token = token(false, Instant.now().plusSeconds(60));
        when(refreshTokenRepository.findByToken("refresh")).thenReturn(Optional.of(token));

        assertSame(token, refreshTokenService.verifyActive("refresh"));
    }

    // handles verifyActive.
    @Test
    void verifyActiveRejectsMissingRevokedAndExpiredTokens() {
        when(refreshTokenRepository.findByToken("missing")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> refreshTokenService.verifyActive("missing"));

        when(refreshTokenRepository.findByToken("revoked")).thenReturn(Optional.of(token(true, Instant.now().plusSeconds(60))));
        assertThrows(BusinessRuleException.class, () -> refreshTokenService.verifyActive("revoked"));

        when(refreshTokenRepository.findByToken("expired")).thenReturn(Optional.of(token(false, Instant.now().minusSeconds(60))));
        assertThrows(BusinessRuleException.class, () -> refreshTokenService.verifyActive("expired"));
    }

    // handles rotate.
    @Test
    void rotateRevokesOldTokenAndCreatesNewToken() {
        RefreshToken oldToken = token(false, Instant.now().plusSeconds(60));
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        RefreshToken newToken = refreshTokenService.rotate(oldToken);

        assertTrue(oldToken.isRevoked());
        assertNotNull(newToken.getToken());
        verify(refreshTokenRepository).save(oldToken);
    }

    // handles revokeAll.
    @Test
    void revokeAllDeletesTokensForUser() {
        refreshTokenService.revokeAll(user);

        verify(refreshTokenRepository).deleteByUser(user);
    }

    // handles revoke.
    @Test
    void revokeMarksTokenAsRevoked() {
        RefreshToken token = token(false, Instant.now().plusSeconds(60));

        refreshTokenService.revoke(token);

        assertTrue(token.isRevoked());
        verify(refreshTokenRepository).save(token);
    }

    private RefreshToken token(boolean revoked, Instant expiresAt) {
        return RefreshToken.builder()
                .id(1L)
                .user(user)
                .token("refresh")
                .revoked(revoked)
                .createdAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
    }
}
