package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.auth.entity.OneTimeToken;
import com.example.shopupu.auth.repository.OneTimeTokenRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.identity.entity.User;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OneTimeTokenServiceTest {

    @Mock
    private OneTimeTokenRepository tokenRepository;

    @InjectMocks
    private OneTimeTokenService tokenService;

    private final User user = User.builder().id(1L).email("user@example.com").build();

    @Test
    void mintStoresHashAndInvalidatesPreviousTokens() {
        when(tokenRepository.save(any(OneTimeToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        String raw = tokenService.mint(user, OneTimeToken.Purpose.PASSWORD_RESET);

        assertNotNull(raw);
        verify(tokenRepository).invalidateAllFor(eq(1L), eq(OneTimeToken.Purpose.PASSWORD_RESET), any(Instant.class));

        ArgumentCaptor<OneTimeToken> captor = ArgumentCaptor.forClass(OneTimeToken.class);
        verify(tokenRepository).save(captor.capture());
        assertNotEquals(raw, captor.getValue().getTokenHash());
        assertEquals(OneTimeTokenService.hash(raw), captor.getValue().getTokenHash());
    }

    @Test
    void consumeBurnsTheTokenOnce() {
        OneTimeToken token = OneTimeToken.builder()
                .user(user)
                .tokenHash(OneTimeTokenService.hash("raw"))
                .purpose(OneTimeToken.Purpose.PASSWORD_RESET)
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        when(tokenRepository.findByTokenHashAndPurpose(OneTimeTokenService.hash("raw"), OneTimeToken.Purpose.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(tokenRepository.save(any(OneTimeToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertSame(user, tokenService.consume("raw", OneTimeToken.Purpose.PASSWORD_RESET));
        assertNotNull(token.getUsedAt());

        // second use of the same token fails
        assertThrows(BusinessRuleException.class,
                () -> tokenService.consume("raw", OneTimeToken.Purpose.PASSWORD_RESET));
    }

    @Test
    void consumeRejectsExpiredUnknownAndWrongPurposeTokens() {
        when(tokenRepository.findByTokenHashAndPurpose(any(), eq(OneTimeToken.Purpose.PASSWORD_RESET)))
                .thenReturn(Optional.empty());
        assertThrows(BusinessRuleException.class,
                () -> tokenService.consume("unknown", OneTimeToken.Purpose.PASSWORD_RESET));

        OneTimeToken expired = OneTimeToken.builder()
                .user(user)
                .tokenHash(OneTimeTokenService.hash("old"))
                .purpose(OneTimeToken.Purpose.EMAIL_VERIFICATION)
                .expiresAt(Instant.now().minusSeconds(60))
                .build();
        when(tokenRepository.findByTokenHashAndPurpose(OneTimeTokenService.hash("old"), OneTimeToken.Purpose.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(expired));
        assertThrows(BusinessRuleException.class,
                () -> tokenService.consume("old", OneTimeToken.Purpose.EMAIL_VERIFICATION));
    }
}
