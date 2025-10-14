package com.example.shopupu.auth.service;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.auth.repository.RefreshTokenRepository;
import com.example.shopupu.identity.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final RefreshTokenRepository refreshTokenRepository;

    @Value("${jwt.refresh-token-ttl-days:7}")
    private long refreshDays;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Генерим крипто-стойкую случайную строку (около 256 бит энтропии). */
    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Создать новый refresh токен для пользователя. */
    public RefreshToken mint(User user) {
        var token = RefreshToken.builder()
                .user(user)
                .token(generateRefreshToken())
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(refreshDays, ChronoUnit.DAYS))
                .revoked(false)
                .build();
        return refreshTokenRepository.save(token);
    }

    /**
     * Проверить, что токен существует, не просрочен и не отозван.
     * Вернуть сам объект (нужен для ротации).
     */
    public RefreshToken verifyActive(String token) {
        var rt = refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token not found"));
        if (rt.isRevoked()) {
            throw new IllegalStateException("Refresh token is revoked");
        }

        if (rt.getExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Refresh token is expired");
        }
        return rt;
    }

    /** Ротация: старый помечаем revoked, выдаём новый. */
    public RefreshToken rotate(RefreshToken oldToken) {
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);
        return mint(oldToken.getUser());
    }

    /** Отозвать все токены пользователя (logout со всех устройств). */
    public void revokeAll(User user) {
        refreshTokenRepository.deleteByUser(user);
    }

    /** Мягко отозвать один токен */
    public void revoke(RefreshToken rt) {
        rt.setRevoked(true);
        refreshTokenRepository.save(rt);
    }
}