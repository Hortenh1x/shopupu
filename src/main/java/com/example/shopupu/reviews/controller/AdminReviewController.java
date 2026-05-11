package com.example.shopupu.reviews.controller;

import com.example.shopupu.reviews.dto.AdminReviewResponse;
import com.example.shopupu.reviews.dto.ReviewStatusRequest;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.mapper.ReviewMapper;
import com.example.shopupu.reviews.service.ReviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@RequestMapping("/api/admin/reviews")
/**
 * describes the AdminReviewController class.
 */
public class AdminReviewController {

    private final ReviewService reviewService;
    private final ReviewMapper reviewMapper;

    @GetMapping
    // handles getReviews.
    public Page<AdminReviewResponse> getReviews(
            @RequestParam(required = false) ReviewStatus status,
            @RequestParam(required = false) Long productId,
            Pageable pageable
    ) {
        return reviewService.getAdminReviews(status, productId, pageable)
                .map(reviewMapper::toAdminResponse);
    }

    @PatchMapping("/{reviewId}/status")
    // handles updateReviewStatus.
    public AdminReviewResponse updateReviewStatus(
            @PathVariable Long reviewId,
            @Valid @RequestBody ReviewStatusRequest request
    ) {
        return reviewMapper.toAdminResponse(reviewService.updateStatus(reviewId, request.status()));
    }

    @DeleteMapping("/{reviewId}")
    // handles deleteReview.
    public ResponseEntity<Void> deleteReview(@PathVariable Long reviewId) {
        reviewService.deleteAdminReview(reviewId);
        return ResponseEntity.noContent().build();
    }
}
