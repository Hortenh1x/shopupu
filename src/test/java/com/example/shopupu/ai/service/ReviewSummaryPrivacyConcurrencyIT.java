package com.example.shopupu.ai.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import com.example.shopupu.ai.event.ProductReviewsChangedEvent;
import com.example.shopupu.ai.gateway.LlmClient;
import com.example.shopupu.ai.model.ReviewSummary;
import com.example.shopupu.ai.repository.ReviewSummaryRepository;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.config.AiProperties;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.repository.ReviewRepository;
import com.example.shopupu.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Both product-lock orderings, real PostgreSQL transactions, synthetic reviews and a mocked LLM only. */
@SpringBootTest(properties = {"ai.enabled=true", "ai.llm-provider=stub", "ai.embedding-provider=stub",
        "ai.review-summary-min-reviews=2"})
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ReviewSummaryPrivacyConcurrencyIT extends PostgresContainerSupport {
    @Autowired private ReviewSummaryService service;
    @Autowired private ReviewSummaryRepository summaries;
    @Autowired private ReviewRepository reviews;
    @Autowired private ProductRepository products;
    @Autowired private CategoryRepository categories;
    @Autowired private UserRepository users;
    @Autowired private ApplicationEventPublisher events;
    @Autowired private TransactionTemplate transactions;
    @Autowired private AiProperties properties;
    @Autowired private CacheManager cacheManager;
    @MockitoBean private LlmClient llm;
    // Invalidation remains real; suppress unrelated post-commit generation in these controlled schedules.
    @MockitoBean private ReviewSummaryListener backgroundGeneration;
    private Long productId;
    private Long reviewId;

    @BeforeEach
    void fixtures() {
        String suffix = Long.toString(System.nanoTime());
        Category category = categories.save(new Category("Synthetic", "summary-race-" + suffix, null, null));
        Product product = products.save(new Product("Synthetic garment", "summary-race-" + suffix,
                "Fictional", BigDecimal.TEN, category));
        productId = product.getId();
        reviewId = review(product, "first-" + suffix, "Synthetic private phrase").getId();
        review(product, "second-" + suffix, "Good synthetic garment");
    }

    @Test
    void erasureCommittedDuringLlmCallPreventsLateSummaryPublication() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(llm.summarizeReviews(anyString(), any())).thenAnswer(invocation -> {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            started.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return Optional.of(oldSummary());
        });
        try (var executor = Executors.newSingleThreadExecutor()) {
            var generation = executor.submit(() -> service.regenerate(productId));
            try {
                assertTrue(started.await(5, TimeUnit.SECONDS));
                transactions.execute(status -> {
                    eraseReview();
                    events.publishEvent(new ProductReviewsChangedEvent(productId));
                    return null;
                });
                assertTrue(summaries.findByProductId(productId).isEmpty());
            } finally {
                release.countDown();
            }
            generation.get(5, TimeUnit.SECONDS);
        }
        assertTrue(summaries.findByProductId(productId).isEmpty());
    }

    @Test
    void erasureDeletesSummaryEvenWhenApplyHeldProductLockFirst() throws Exception {
        CountDownLatch inserted = new CountDownLatch(1);
        CountDownLatch reviewFlushed = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var apply = executor.submit(() -> transactions.execute(status -> {
                assertTrue(summaries.lockProduct(productId));
                summaries.upsert(productId, oldSummary(), 2, "synthetic-model");
                inserted.countDown();
                await(release);
                return null;
            }));
            try {
                assertTrue(inserted.await(5, TimeUnit.SECONDS));
                var erasure = executor.submit(() -> transactions.execute(status -> {
                    eraseReview();
                    reviews.flush();
                    reviewFlushed.countDown();
                    events.publishEvent(new ProductReviewsChangedEvent(productId));
                    return null;
                }));
                assertTrue(reviewFlushed.await(5, TimeUnit.SECONDS));
                assertThrows(TimeoutException.class, () -> erasure.get(100, TimeUnit.MILLISECONDS));
                release.countDown();
                apply.get(5, TimeUnit.SECONDS);
                erasure.get(5, TimeUnit.SECONDS);
            } finally {
                release.countDown();
            }
        }
        assertTrue(summaries.findByProductId(productId).isEmpty());
    }

    @Test
    void invalidationStillRunsWhenAiIsDisabledAndDoesNotReadOldCache() {
        summaries.upsert(productId, oldSummary(), 2, "synthetic-model");
        var cache = cacheManager.getCache("reviewSummary");
        assertNotNull(cache);
        cache.put(productId, summaries.findByProductId(productId).orElseThrow());
        properties.setEnabled(false);
        try {
            transactions.execute(status -> {
                eraseReview();
                events.publishEvent(new ProductReviewsChangedEvent(productId));
                return null;
            });
            assertTrue(summaries.findByProductId(productId).isEmpty());
            assertNull(cache.get(productId));
            // Even a late cache population cannot affect new reads: getSummary has no result cache.
            cache.put(productId, "Synthetic old cache load");
            assertThrows(ResourceNotFoundException.class, () -> service.getSummary(productId));
        } finally {
            cache.evict(productId);
            properties.setEnabled(true);
        }
    }

    private Review review(Product product, String suffix, String text) {
        User user = users.save(User.builder().email(suffix + "@example.invalid").passwordHash("synthetic-hash")
                .enabled(true).build());
        Review review = new Review();
        review.setProduct(product);
        review.setUser(user);
        review.setRating(5);
        review.setBody(text);
        review.setStatus(ReviewStatus.APPROVED);
        return reviews.save(review);
    }

    private void eraseReview() {
        Review review = reviews.findById(reviewId).orElseThrow();
        review.setBody("[deleted]");
        review.setStatus(ReviewStatus.DELETED);
        reviews.save(review);
    }

    private ReviewSummary oldSummary() {
        return new ReviewSummary("Synthetic private phrase", List.of(), List.of(), ReviewSummary.Sentiment.POSITIVE);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}
