package com.example.shopupu.auth.service;

import com.example.shopupu.auth.repository.RefreshTokenRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Retention: expired refresh tokens are useless and only accumulate PII-adjacent data. */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;

    @Transactional
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpiredTokens() {
        int removed = refreshTokenRepository.deleteAllExpiredBefore(Instant.now().minus(1, ChronoUnit.DAYS));
        if (removed > 0) {
            log.info("Purged {} expired refresh tokens", removed);
        }
    }
}
