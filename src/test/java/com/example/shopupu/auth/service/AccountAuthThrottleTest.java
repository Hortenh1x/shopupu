package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Clock;
import org.junit.jupiter.api.Test;

class AccountAuthThrottleTest {
    @Test void attemptsDoNotExtendTheFixedLockoutAndOperationsHaveSeparateBudgets() {
        Clock clock = mock(Clock.class); when(clock.millis()).thenReturn(0L);
        var limiter = new AccountAuthThrottle(clock);
        for (int i = 0; i < 10; i++) limiter.check("login", "USER@example.com");
        assertEquals(60, assertThrows(AuthRateLimitException.class,
                () -> limiter.check("login", "user@example.com")).retryAfterSeconds());
        assertDoesNotThrow(() -> limiter.check("reset", "user@example.com"));
        when(clock.millis()).thenReturn(59_000L);
        assertEquals(1, assertThrows(AuthRateLimitException.class,
                () -> limiter.check("login", "user@example.com")).retryAfterSeconds());
        when(clock.millis()).thenReturn(60_000L);
        assertDoesNotThrow(() -> limiter.check("login", "user@example.com"));
    }
}
