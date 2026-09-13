package com.example.shopupu.ai.service;

import com.example.shopupu.ai.event.ProductReviewsChangedEvent;
import com.example.shopupu.ai.repository.ReviewSummaryRepository;
import com.example.shopupu.reviews.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Privacy invalidation is synchronous and independent of AI availability or background job success. */
@Component
@RequiredArgsConstructor
public class ReviewSummaryInvalidationListener {
    private final ReviewRepository reviews;
    private final ReviewSummaryRepository summaries;
    private final CacheManager cacheManager;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void invalidate(ProductReviewsChangedEvent event) {
        // Preserve review-row -> product-row ordering used by GDPR. save() alone may defer the UPDATE.
        reviews.flush();
        if (summaries.lockProduct(event.productId())) summaries.deleteByProductId(event.productId());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void evictOldCacheEntry(ProductReviewsChangedEvent event) {
        var cache = cacheManager.getCache("reviewSummary");
        if (cache != null) cache.evict(event.productId());
    }
}
