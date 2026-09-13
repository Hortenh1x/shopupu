package com.example.shopupu.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record LoginResponse(String status, String accessToken, String refreshToken,
                            String challengeToken, Instant expiresAt, List<String> recoveryCodes) {
    public static LoginResponse authenticated(String access, String refresh) {
        return new LoginResponse("AUTHENTICATED", access, refresh, null, null, null);
    }
    public static LoginResponse challenge(String status, String token, Instant expiresAt) {
        return new LoginResponse(status, null, null, token, expiresAt, null);
    }
}
