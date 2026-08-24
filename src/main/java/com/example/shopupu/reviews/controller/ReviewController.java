package com.example.shopupu.reviews.controller;

import com.example.shopupu.reviews.dto.CreateReviewRequest;
import com.example.shopupu.reviews.dto.ProductRatingSummaryResponse;
import com.example.shopupu.reviews.dto.ReviewResponse;
import com.example.shopupu.reviews.dto.UpdateReviewRequest;
import com.example.shopupu.reviews.mapper.ReviewMapper;
import com.example.shopupu.reviews.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
/**
 * describes the ReviewController class.
 */
public class ReviewController {

    private final ReviewService reviewService;
    private final ReviewMapper reviewMapper;

    @GetMapping("/api/v1/catalog/products/{productId}/reviews")
    // handles getProductReviews.
    public Page<ReviewResponse> getProductReviews(@PathVariable Long productId, Pageable pageable) {
        return reviewService.getPublishedReviews(productId, pageable)
                .map(reviewMapper::toResponse);
    }

    @GetMapping("/api/v1/catalog/products/{productId}/rating")
    // handles getProductRating.
    public ProductRatingSummaryResponse getProductRating(@PathVariable Long productId) {
        return reviewService.getRatingSummary(productId);
    }

    @PostMapping("/api/v1/products/{productId}/reviews")
    // handles createReview.
    public ReviewResponse createReview(
            @PathVariable Long productId,
            @Valid @RequestBody CreateReviewRequest request
    ) {
        var review = reviewService.createReview(productId, request.rating(), request.body(), request.orderId());
        return reviewMapper.toResponse(review);
    }

    @PutMapping("/api/v1/reviews/{reviewId}")
    // handles updateReview.
    public ReviewResponse updateReview(
            @PathVariable Long reviewId,
            @Valid @RequestBody UpdateReviewRequest request
    ) {
        var review = reviewService.updateReview(reviewId, request.rating(), request.body());
        return reviewMapper.toResponse(review);
    }

    @DeleteMapping("/api/v1/reviews/{reviewId}")
    // handles deleteReview.
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteOwnReview(reviewId);
        return ResponseEntity.noContent().build();
    }
}
