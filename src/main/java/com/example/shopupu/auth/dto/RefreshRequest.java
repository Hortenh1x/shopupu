package com.example.shopupu.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * describes the RefreshRequest record.
 */
public record RefreshRequest(
        @NotBlank
        String refreshToken
) {
}