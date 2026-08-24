package com.example.shopupu.ai.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.shopupu.ai.gateway.LlmClient;
import com.example.shopupu.ai.model.ReviewSummary;
import com.example.shopupu.ai.repository.ReviewSummaryRepository;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.config.AiProperties;
import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.repository.ReviewRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.data.domain.PageImpl;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * describes the ReviewSummaryServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class ReviewSummaryServiceTest {

    @Mock
    private LlmClient llmClient;

    @Mock
    private ReviewSummaryRepository summaryRepository;

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private CacheManager cacheManager;

    private AiProperties aiProperties;
    private ReviewSummaryService service;

    @BeforeEach
    void setUp() {
        aiProperties = new AiProperties();
        aiProperties.setEnabled(true);
        aiProperties.setReviewSummaryMinReviews(2);
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation ->
                invocation.<TransactionCallback<Object>>getArgument(0).doInTransaction(null));
        service = new ReviewSummaryService(aiProperties, llmClient, summaryRepository,
                reviewRepository, productRepository, transactionTemplate, cacheManager,
                new SimpleMeterRegistry());
    }

    @Test
    void regenerateUpsertsSummaryFromApprovedReviews() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product()));
        when(reviewRepository.findByProductIdAndStatus(eq(1L), eq(ReviewStatus.APPROVED), any()))
                .thenReturn(new PageImpl<>(List.of(review(5, "Great"), review(4, "Good"))));
        ReviewSummary summary = new ReviewSummary("tldr", List.of("warm"), List.of(),
                ReviewSummary.Sentiment.POSITIVE);
        when(llmClient.summarizeReviews(eq("Hoodie"), any())).thenReturn(Optional.of(summary));

        service.regenerate(1L);

        verify(summaryRepository).upsert(1L, summary, 2, aiProperties.getLlmModel());
    }

    @Test
    void regenerateDeletesStaleSummaryBelowThreshold() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product()));
        when(reviewRepository.findByProductIdAndStatus(eq(1L), eq(ReviewStatus.APPROVED), any()))
                .thenReturn(new PageImpl<>(List.of(review(5, "Great"))));

        service.regenerate(1L);

        verify(summaryRepository).deleteByProductId(1L);
        verifyNoInteractions(llmClient);
    }

    @Test
    void regenerateKeepsExistingSummaryWhenLlmIsUnavailable() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product()));
        when(reviewRepository.findByProductIdAndStatus(eq(1L), eq(ReviewStatus.APPROVED), any()))
                .thenReturn(new PageImpl<>(List.of(review(5, "Great"), review(4, "Good"))));
        when(llmClient.summarizeReviews(anyString(), any())).thenReturn(Optional.empty());

        service.regenerate(1L);

        verify(summaryRepository, never()).upsert(any(), any(), anyInt(), any());
    }

    @Test
    void regenerateIsNoOpWhenAiIsDisabled() {
        aiProperties.setEnabled(false);

        service.regenerate(1L);

        verifyNoInteractions(llmClient, summaryRepository, reviewRepository);
    }

    @Test
    void getSummaryFailsWithNotFoundWhenAbsent() {
        when(productRepository.existsById(1L)).thenReturn(true);
        when(summaryRepository.findByProductId(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.getSummary(1L));
    }

    private Product product() {
        Product product = new Product();
        product.setId(1L);
        product.setTitle("Hoodie");
        return product;
    }

    private Review review(int rating, String body) {
        Review review = new Review();
        review.setRating(rating);
        review.setBody(body);
        return review;
    }
}
