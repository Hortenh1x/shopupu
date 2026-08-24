package com.example.shopupu.auth.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * describes the RegisterRequest record.
 */
public record RegisterRequest(
        @Email
        @NotBlank
        String email,

        @NotBlank
        @Size(min = 8, max = 128)
        String password,

        @NotBlank
        String passwordConfirm
) {

    /** Guards against a mistyped password by requiring the confirmation to match. */
    @AssertTrue(message = "passwords do not match")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(passwordConfirm);
    }
}
