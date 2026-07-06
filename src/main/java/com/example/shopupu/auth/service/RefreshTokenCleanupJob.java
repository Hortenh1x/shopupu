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
    private final com.example.shopupu.auth.repository.OneTimeTokenRepository oneTimeTokenRepository;

    @Transactional
    @Scheduled(cron = "0 30 3 * * *")
    public void purgeExpiredTokens() {
        Instant cutoff = Instant.now().minus(1, ChronoUnit.DAYS);
        int removed = refreshTokenRepository.deleteAllExpiredBefore(cutoff);
        int removedOneTime = oneTimeTokenRepository.deleteAllExpiredBefore(cutoff);
        if (removed > 0 || removedOneTime > 0) {
            log.info("Purged {} expired refresh tokens and {} one-time tokens", removed, removedOneTime);
        }
    }
}
