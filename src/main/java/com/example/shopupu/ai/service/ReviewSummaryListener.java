package com.example.shopupu.ai.service;

import com.example.shopupu.ai.event.ProductReviewsChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Regenerates the review summary AFTER the moderation transaction commits and
 * on the AI pool: a broken LLM provider can never fail review moderation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReviewSummaryListener {

    private final ReviewSummaryService reviewSummaryService;

    @Async("aiExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onProductReviewsChanged(ProductReviewsChangedEvent event) {
        try {
            reviewSummaryService.regenerate(event.productId());
        } catch (Exception ex) {
            // never propagate: summaries are a best-effort side effect
            log.warn("Failed to regenerate review summary for product {}", event.productId(), ex);
        }
    }
}
