package com.example.shopupu.auth.service;

import com.example.shopupu.common.exception.UnauthorizedException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.service.UserService;
import com.example.shopupu.security.JwtTokenProvider;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;
    private final com.example.shopupu.common.audit.AuditService auditService;

    public record TokenPair(String accessToken, String refreshToken) {}

    @Transactional
    public TokenPair login(String email, String password) {
        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        } catch (AuthenticationException e) {
            auditService.record(email, "LOGIN_FAILED", "user", null, null);
            // Uniform message: no hint whether the account exists (SEC-16).
            throw new UnauthorizedException("Wrong login or password");
        }
        User user = userService.getByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Wrong login or password"));
        auditService.record(email, "LOGIN_SUCCEEDED", "user", String.valueOf(user.getId()), null);
        return issueTokens(user);
    }

    @Transactional
    public TokenPair issueTokens(User user) {
        UserDetails principal = toPrincipal(user);

        String access = jwtTokenProvider.generateToken(principal);
        var refresh = refreshTokenService.mint(user);

        return new TokenPair(access, refresh.rawToken());
    }

    @Transactional
    public TokenPair refresh(String refreshToken) {
        var oldToken = refreshTokenService.verifyActive(refreshToken);
        var user = oldToken.getUser();
        var newRt = refreshTokenService.rotate(oldToken);

        UserDetails principal = toPrincipal(user);

        String newAccess = jwtTokenProvider.generateToken(principal);
        return new TokenPair(newAccess, newRt.rawToken());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokenService.logout(refreshToken);
    }

    /** Changes the password and invalidates every active session of the user (AUTH-12). */
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userService.changePassword(email, currentPassword, newPassword);
        refreshTokenService.revokeAll(user);
        auditService.record(email, "PASSWORD_CHANGED", "user", String.valueOf(user.getId()),
                "All sessions revoked");
    }

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
