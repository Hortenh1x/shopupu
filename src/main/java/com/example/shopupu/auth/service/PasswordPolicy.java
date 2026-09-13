package com.example.shopupu.auth.service;

import com.example.shopupu.common.exception.BadRequestException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;

/** Policy for new credentials only. Existing credentials remain valid for login. */
@Component
public class PasswordPolicy {
    // Small manually authored, project-owned examples of predictable full passwords.
    // No external corpus/license. This is NOT a comprehensive breached-password database.
    private static final Set<String> COMMON = Set.of(
            "password", "password123", "password12345678", "passwordpassword",
            "passwordpassword123", "password123456789", "123456789012345",
            "1234567890123456", "12345678901234567890", "qwertyuiopasdfgh",
            "qwertyuiopasdfghjkl", "letmeinletmeinletmein", "adminadminadmin",
            "administrator123", "iloveyouiloveyou", "welcome123456789");

    public void validate(String password) {
        if (password == null || password.codePointCount(0, password.length()) < 15) {
            throw new BadRequestException("Password must contain at least 15 characters");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new BadRequestException("Password must be at most 72 UTF-8 bytes");
        }
        if (COMMON.contains(password.toLowerCase(Locale.ROOT))) {
            throw new BadRequestException("Choose a less common password");
        }
    }
}
