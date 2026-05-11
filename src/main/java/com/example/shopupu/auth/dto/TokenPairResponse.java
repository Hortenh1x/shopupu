package com.example.shopupu.auth.dto;

/**
 * describes the TokenPairResponse record.
 */
public record TokenPairResponse(
        String accessToken,
        String refreshToken
) {
}