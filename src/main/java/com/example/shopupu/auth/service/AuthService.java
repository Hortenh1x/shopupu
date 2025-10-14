package com.example.shopupu.auth.service;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final UserService userService;

    /** DTO для пары токенов */
    public record TokenPair(String accessToken, String refreshToken) {}

    /** создать пару при логине/регистрации */
    public TokenPair issueTokens(User user) {
        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles("CUSTOMER")
                .build();

        String access = jwtTokenProvider.generateToken(principal);
        RefreshToken refresh = refreshTokenService.mint(user);

        return new TokenPair(access, refresh.getToken());
    }

    /** обновить access по refresh, с ротацией refresh */
    public TokenPair refresh(String refreshToken) {
        var oldToken = refreshTokenService.verifyActive(refreshToken);
        var user = oldToken.getUser();
        var newRt = refreshTokenService.rotate(oldToken);

        UserDetails principal = org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .roles("CUSTOMER")
                .build();

        String newAccess = jwtTokenProvider.generateToken(principal);
        return new TokenPair(newAccess, newRt.getToken());
    }
}