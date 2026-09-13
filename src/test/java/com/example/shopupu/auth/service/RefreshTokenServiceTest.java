package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.auth.repository.RefreshTokenRepository;
import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.config.JwtProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

class RefreshTokenServiceTest {
    private final RefreshTokenRepository tokens = mock(RefreshTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final PlatformTransactionManager tx = mock(PlatformTransactionManager.class);
    private final User user = User.builder().id(1L).email("user@example.com").passwordHash("hash").build();
    private RefreshTokenService service;
    @BeforeEach void setup() { service = new RefreshTokenService(tokens, new JwtProperties(), tx, users); }
    private void lock() { when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user)); }
    private void rotation(RefreshToken token) {
        lock(); when(tx.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(tokens.findUserIdByToken(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(1L));
        when(tokens.findByToken(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(token));
    }
    @Test void mintStoresHashAndCurrentSecurityVersion() {
        lock(); user.setAuthVersion(8); when(tokens.save(any())).thenAnswer(i -> i.getArgument(0));
        var minted = service.mint(user);
        assertNotEquals(minted.rawToken(), minted.entity().getToken());
        assertEquals(RefreshTokenService.hash(minted.rawToken()), minted.entity().getToken());
        assertEquals(8, minted.entity().getAuthVersion());
    }
    @Test void reuseCommitsAllSessionRevocationBeforeUnauthorized() {
        rotation(token(true));
        assertThrows(UnauthorizedException.class, () -> service.rotate("raw"));
        verify(tokens).revokeAllByUser(user); verify(tx).commit(any()); verify(tx, never()).rollback(any());
        assertEquals(1, user.getAuthVersion());
    }
    @Test void guardedConsumeFailureCannotMintSuccessor() {
        rotation(token(false)); when(tokens.consume(any(), any())).thenReturn(0);
        assertThrows(UnauthorizedException.class, () -> service.rotate("raw"));
        verify(tokens, never()).save(any());
    }
    @Test void rotationPreservesMfaAssuranceTimestamp() {
        RefreshToken old = token(false); Instant verified = Instant.now().minusSeconds(90); old.setMfaVerifiedAt(verified);
        rotation(old); when(tokens.consume(any(), any())).thenReturn(1); when(tokens.save(any())).thenAnswer(i -> i.getArgument(0));
        assertEquals(verified, service.rotate("raw").entity().getMfaVerifiedAt());
    }
    @Test void logoutCannotRevokeAnotherAccountToken() {
        lock(); when(tokens.findUserIdByToken(RefreshTokenService.hash("raw"))).thenReturn(Optional.of(1L));
        service.logout("raw", "someone-else@example.com"); verify(tokens, never()).findByToken(any());
    }
    private RefreshToken token(boolean revoked) { return RefreshToken.builder().id(1L).user(user)
            .expiresAt(Instant.now().plusSeconds(60)).revoked(revoked).build(); }
}
