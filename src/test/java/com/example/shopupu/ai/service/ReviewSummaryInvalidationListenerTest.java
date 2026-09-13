package com.example.shopupu.ai.service;

import static org.mockito.Mockito.*;

import com.example.shopupu.ai.event.ProductReviewsChangedEvent;
import com.example.shopupu.ai.repository.ReviewSummaryRepository;
import com.example.shopupu.reviews.repository.ReviewRepository;
import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;

class ReviewSummaryInvalidationListenerTest {
    @Test
    void flushesReviewWritesBeforeTakingTheProductLockAndDeletingSummary() {
        var reviews = mock(ReviewRepository.class);
        var summaries = mock(ReviewSummaryRepository.class);
        var cacheManager = mock(CacheManager.class);
        when(summaries.lockProduct(1L)).thenReturn(true);
        var listener = new ReviewSummaryInvalidationListener(reviews, summaries, cacheManager);
        listener.invalidate(new ProductReviewsChangedEvent(1L));
        var order = inOrder(reviews, summaries);
        order.verify(reviews).flush();
        order.verify(summaries).lockProduct(1L);
        order.verify(summaries).deleteByProductId(1L);
        verifyNoInteractions(cacheManager);
    }
}
