package com.example.shopupu.ai.service;

import com.example.shopupu.ai.dto.ReviewSummaryResponse;
import com.example.shopupu.ai.gateway.LlmClient;
import com.example.shopupu.ai.model.ReviewSummary;
import com.example.shopupu.ai.model.TextScript;
import com.example.shopupu.ai.repository.ReviewSummaryRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.config.AiProperties;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.repository.ReviewRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "What customers say" summaries. Only APPROVED, HTML-sanitized review texts
 * reach the LLM (approval/sanitization do not prove absence of PII). Follows ADR-0003:
 * shape: TX(load snapshot) -> LLM HTTP outside any transaction -> TX(upsert).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewSummaryService {

    private static final int MAX_REVIEWS_PER_SUMMARY = 50;

    private final AiProperties aiProperties;
    private final LlmClient llmClient;
    private final ReviewSummaryRepository summaryRepository;
    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final TransactionTemplate transactionTemplate;
    private final CacheManager cacheManager;
    private final io.micrometer.core.instrument.MeterRegistry meterRegistry;

    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummary(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product with id " + productId + " not found");
        }
        return summaryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review summary for product " + productId + " is not available"));
    }

    @Transactional(propagation = Propagation.NEVER)
    public void regenerate(Long productId) {
        if (!aiProperties.isEnabled()) {
            return;
        }
        ReviewsSnapshot snapshot = transactionTemplate.execute(tx -> {
            if (!summaryRepository.lockProduct(productId)) return null;
            ReviewsSnapshot captured = loadApprovedReviews(productId);
            if (captured != null && captured.reviews().size() < aiProperties.getReviewSummaryMinReviews()) {
                summaryRepository.deleteByProductId(productId);
                return null;
            }
            return captured;
        });
        // Reads no longer use a cache: a concurrent cache loader must not restore erased text.
        evict(productId);
        if (snapshot == null) {
            return;
        }
        summarize(snapshot).ifPresent(summary -> {
            boolean applied = Boolean.TRUE.equals(transactionTemplate.execute(tx -> {
                if (!aiProperties.isEnabled() || !summaryRepository.lockProduct(productId)) return false;
                ReviewsSnapshot current = loadApprovedReviews(productId);
                if (!snapshot.equals(current)) return false;
                summaryRepository.upsert(productId, summary, snapshot.reviews().size(), aiProperties.getLlmModel());
                return true;
            }));
            if (applied) evict(productId);
        });
    }

    @Async("aiExecutor")
    public void refreshAllAsync() {
        if (!aiProperties.isEnabled()) return;
        List<Long> productIds = transactionTemplate.execute(tx ->
                reviewRepository.findProductIdsWithApprovedCountAtLeast(
                        aiProperties.getReviewSummaryMinReviews()));
        if (productIds == null) {
            return;
        }
        for (Long productId : productIds) {
            try {
                regenerate(productId);
            } catch (Exception ex) {
                log.warn("Review summary refresh unavailable for product {}", productId);
            }
        }
        log.info("Review summary refresh finished for {} products", productIds.size());
    }

    private Optional<ReviewSummary> summarize(ReviewsSnapshot snapshot) {
        Optional<ReviewSummary> summary =
                llmClient.summarizeReviews(snapshot.productTitle(), snapshot.reviewLines());
        if (summary.isPresent() && driftedLanguage(snapshot, summary.get())) {
            // asking for a language is not enough: a run over this catalogue produced
            // German summaries, and once the prompt forbade that, Ukrainian ones — for
            // reviews written entirely in English. No summary beats a wrong-language one.
            log.warn("Discarding summary for '{}': written in a different script than its reviews",
                    snapshot.productTitle());
            meterRegistry.counter("shopupu.ai", "op", "review_summary", "result", "wrong_language")
                    .increment();
            return Optional.empty();
        }
        meterRegistry.counter("shopupu.ai", "op", "review_summary",
                "result", summary.isPresent() ? "ok" : "empty").increment();
        return summary;
    }

    private boolean driftedLanguage(ReviewsSnapshot snapshot, ReviewSummary summary) {
        TextScript reviews = TextScript.of(String.join(" ", snapshot.reviewLines()));
        TextScript written = TextScript.of(summary.tldr());
        return reviews != TextScript.UNDETERMINED
                && written != TextScript.UNDETERMINED
                && reviews != written;
    }

    /** Snapshot mapped inside the TX (OSIV off); reviews are capped at the most recent N. */
    private ReviewsSnapshot loadApprovedReviews(Long productId) {
        return productRepository.findById(productId).map(product -> {
            var page = reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED,
                    PageRequest.of(0, MAX_REVIEWS_PER_SUMMARY, Sort.by(Sort.Direction.DESC, "createdAt", "id")));
            List<ApprovedReviewSnapshot> reviews = page.getContent().stream()
                    .map(review -> new ApprovedReviewSnapshot(review.getId(), review.getRating(),
                            review.getBody(), review.getUpdatedAt()))
                    .toList();
            return new ReviewsSnapshot(product.getTitle(), page.getTotalElements(), reviews);
        }).orElse(null);
    }

    private void evict(Long productId) {
        var cache = cacheManager.getCache("reviewSummary");
        if (cache != null) {
            cache.evict(productId);
        }
    }

    private record ApprovedReviewSnapshot(Long id, Integer rating, String body, Instant updatedAt) {
    }

    private record ReviewsSnapshot(String productTitle, long approvedCount, List<ApprovedReviewSnapshot> reviews) {
        List<String> reviewLines() {
            return reviews.stream().map(review -> "[" + review.rating() + "/5] " + review.body()).toList();
        }
    }
}
