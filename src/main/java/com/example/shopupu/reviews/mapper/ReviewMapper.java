package com.example.shopupu.reviews.mapper;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.reviews.dto.AdminReviewResponse;
import com.example.shopupu.reviews.dto.ReviewResponse;
import com.example.shopupu.reviews.entity.Review;
import org.springframework.stereotype.Component;

@Component
public class ReviewMapper {

    public ReviewResponse toResponse(Review review) {
        User user = review.getUser();
        // never expose the email publicly (PII)
        String displayName = user.getUsername() != null ? user.getUsername() : "Customer";
        return new ReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                user.getId(),
                displayName,
                review.getRating(),
                review.getTitle(),
                review.getBody(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }

    public AdminReviewResponse toAdminResponse(Review review) {
        User user = review.getUser();
        return new AdminReviewResponse(
                review.getId(),
                review.getProduct().getId(),
                review.getProduct().getTitle(),
                user.getId(),
                user.getEmail(),
                review.getRating(),
                review.getTitle(),
                review.getBody(),
                review.getStatus(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}
