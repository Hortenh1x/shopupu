package com.example.shopupu.auth.service;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
/**
 * describes the AuthService class.
 */
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;


    /**
     * describes the TokenPair record.
     */
    public record TokenPair(String accessToken, String refreshToken) {}


    // handles issueTokens.
    public TokenPair issueTokens(User user) {
        UserDetails principal = toPrincipal(user);

        String access = jwtTokenProvider.generateToken(principal);
        RefreshToken refresh = refreshTokenService.mint(user);

        return new TokenPair(access, refresh.getToken());
    }


    // handles refresh.
    public TokenPair refresh(String refreshToken) {
        var oldToken = refreshTokenService.verifyActive(refreshToken);
        var user = oldToken.getUser();
        var newRt = refreshTokenService.rotate(oldToken);

        UserDetails principal = toPrincipal(user);

        String newAccess = jwtTokenProvider.generateToken(principal);
        return new TokenPair(newAccess, newRt.getToken());
    }

    // handles toPrincipal.
    private UserDetails toPrincipal(User user) {
        var authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName()))
                .collect(Collectors.toSet());
        return org.springframework.security.core.userdetails.User
                .withUsername(user.getEmail())
                .password(user.getPasswordHash())
                .authorities(authorities)
                .disabled(!user.isEnabled())
                .build();
    }
}