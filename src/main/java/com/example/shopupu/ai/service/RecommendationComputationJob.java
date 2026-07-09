package com.example.shopupu.ai.service;

import com.example.shopupu.ai.repository.ProductRecommendationRepository;
import com.example.shopupu.config.AiProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Nightly "bought together" rebuild (mirrors OrderExpirationJob): pure SQL over
 * paid orders, so it is cheap and needs no AI provider.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecommendationComputationJob {

    private static final int TOP_PER_PRODUCT = 10;
    private static final int MIN_PAIR_COUNT = 2;

    private final AiProperties aiProperties;
    private final ProductRecommendationRepository recommendationRepository;
    private final CacheManager cacheManager;

    @Scheduled(cron = "0 30 3 * * *")
    public void recomputeNightly() {
        if (!aiProperties.isEnabled()) {
            return;
        }
        recompute();
    }

    @Async("aiExecutor")
    public void recomputeAsync() {
        try {
            recompute();
        } catch (Exception ex) {
            log.warn("Bought-together recomputation failed", ex);
        }
    }

    public int recompute() {
        int pairs = recommendationRepository.recomputeBoughtTogether(TOP_PER_PRODUCT, MIN_PAIR_COUNT);
        var cache = cacheManager.getCache("recommendations");
        if (cache != null) {
            cache.clear();
        }
        log.info("Recomputed bought-together recommendations: {} pairs", pairs);
        return pairs;
    }
}
