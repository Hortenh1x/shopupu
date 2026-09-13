package com.example.shopupu.auth.service;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.common.exception.BadRequestException;
import org.junit.jupiter.api.Test;

class PasswordPolicyTest {
    private final PasswordPolicy policy = new PasswordPolicy();

    @Test void requiresLengthWithoutCompositionRules() {
        assertThrows(BadRequestException.class, () -> policy.validate("shortPassword!"));
        assertDoesNotThrow(() -> policy.validate("a quiet meadow beneath the stars"));
    }
    @Test void rejectsCommonFullPasswordsAndUtf8BcryptOverflow() {
        assertThrows(BadRequestException.class, () -> policy.validate("passwordpassword"));
        assertThrows(BadRequestException.class, () -> policy.validate("я".repeat(37)));
        assertDoesNotThrow(() -> policy.validate("я".repeat(36)));
    }
}
