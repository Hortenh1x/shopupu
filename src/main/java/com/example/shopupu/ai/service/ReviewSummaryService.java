package com.example.shopupu.ai.service;

import com.example.shopupu.ai.dto.ReviewSummaryResponse;
import com.example.shopupu.ai.gateway.LlmClient;
import com.example.shopupu.ai.model.ReviewSummary;
import com.example.shopupu.ai.repository.ReviewSummaryRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.config.AiProperties;
import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.repository.ReviewRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * "What customers say" summaries. Only APPROVED (already Jsoup-sanitized,
 * PII-free) review texts reach the LLM, and the call follows the ADR-0003
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

    @Cacheable(cacheNames = "reviewSummary", key = "#productId")
    @Transactional(readOnly = true)
    public ReviewSummaryResponse getSummary(Long productId) {
        if (!productRepository.existsById(productId)) {
            throw new ResourceNotFoundException("Product with id " + productId + " not found");
        }
        return summaryRepository.findByProductId(productId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Review summary for product " + productId + " is not available"));
    }

    public void regenerate(Long productId) {
        if (!aiProperties.isEnabled()) {
            return;
        }
        ReviewsSnapshot snapshot = transactionTemplate.execute(tx -> loadApprovedReviews(productId));
        if (snapshot == null) {
            return;
        }
        if (snapshot.reviewLines().size() < aiProperties.getReviewSummaryMinReviews()) {
            // too few approved reviews (or reviews were removed): drop any stale summary
            summaryRepository.deleteByProductId(productId);
            evict(productId);
            return;
        }
        summarize(snapshot).ifPresent(summary -> {
            summaryRepository.upsert(productId, summary,
                    snapshot.reviewLines().size(), aiProperties.getLlmModel());
            evict(productId);
        });
    }

    @Async("aiExecutor")
    public void refreshAllAsync() {
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
                log.warn("Review summary refresh failed for product {}", productId, ex);
            }
        }
        log.info("Review summary refresh finished for {} products", productIds.size());
    }

    private Optional<ReviewSummary> summarize(ReviewsSnapshot snapshot) {
        Optional<ReviewSummary> summary =
                llmClient.summarizeReviews(snapshot.productTitle(), snapshot.reviewLines());
        meterRegistry.counter("shopupu.ai", "op", "review_summary",
                "result", summary.isPresent() ? "ok" : "empty").increment();
        return summary;
    }

    /** Snapshot mapped inside the TX (OSIV off); reviews are capped at the most recent N. */
    private ReviewsSnapshot loadApprovedReviews(Long productId) {
        return productRepository.findById(productId).map(product -> {
            var page = reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED,
                    PageRequest.of(0, MAX_REVIEWS_PER_SUMMARY, Sort.by(Sort.Direction.DESC, "createdAt")));
            List<String> lines = page.getContent().stream()
                    .map(ReviewSummaryService::toReviewLine)
                    .toList();
            return new ReviewsSnapshot(product.getTitle(), lines);
        }).orElse(null);
    }

    private static String toReviewLine(Review review) {
        return "[" + review.getRating() + "/5] " + review.getBody();
    }

    private void evict(Long productId) {
        var cache = cacheManager.getCache("reviewSummary");
        if (cache != null) {
            cache.evict(productId);
        }
    }

    private record ReviewsSnapshot(String productTitle, List<String> reviewLines) {
    }
}
