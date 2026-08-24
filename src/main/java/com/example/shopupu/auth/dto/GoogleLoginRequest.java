package com.example.shopupu.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * The Google ID token (JWT credential) obtained by the browser from Google
 * Identity Services. The backend verifies it against Google's keys before
 * issuing its own session tokens.
 */
public record GoogleLoginRequest(
        @NotBlank
        String idToken
) {
}
