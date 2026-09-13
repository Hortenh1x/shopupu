package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.shopupu.auth.entity.OneTimeToken;
import com.example.shopupu.auth.repository.OneTimeTokenRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OneTimeTokenServiceTest {
    private final OneTimeTokenRepository tokens = mock(OneTimeTokenRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final OneTimeTokenService service = new OneTimeTokenService(tokens, users);
    private final User user = User.builder().id(1L).email("user@example.com").build();
    @Test void mintLocksAccountBeforeInvalidateAndInsertAndStoresOnlyHash() {
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        String raw = service.mint(user, OneTimeToken.Purpose.PASSWORD_RESET);
        var ordered = inOrder(users, tokens); ordered.verify(users).findByIdForUpdate(1L);
        ordered.verify(tokens).invalidateAllFor(eq(1L), eq(OneTimeToken.Purpose.PASSWORD_RESET), any());
        var capture = ArgumentCaptor.forClass(OneTimeToken.class); ordered.verify(tokens).save(capture.capture());
        assertEquals(OneTimeTokenService.hash(raw), capture.getValue().getTokenHash());
        assertNotEquals(raw, capture.getValue().getTokenHash());
    }
    @Test void guardedConsumeRejectsReuseExpiryAndWrongPurpose() {
        when(tokens.findUserId(any(), eq(OneTimeToken.Purpose.PASSWORD_RESET))).thenReturn(Optional.of(1L));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(tokens.consume(any(), eq(OneTimeToken.Purpose.PASSWORD_RESET), any(Instant.class))).thenReturn(1, 0);
        assertSame(user, service.consume("raw", OneTimeToken.Purpose.PASSWORD_RESET));
        assertThrows(BusinessRuleException.class, () -> service.consume("raw", OneTimeToken.Purpose.PASSWORD_RESET));
        assertThrows(BusinessRuleException.class, () -> service.consume("raw", OneTimeToken.Purpose.EMAIL_VERIFICATION));
    }
}
