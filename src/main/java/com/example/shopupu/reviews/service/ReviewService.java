package com.example.shopupu.reviews.service;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ConflictException;
import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.reviews.dto.ProductRatingSummaryResponse;
import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.repository.ReviewRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final AccessControlService accessControlService;

    @Transactional(readOnly = true)
    public Page<Review> getPublishedReviews(Long productId, Pageable pageable) {
        requireProduct(productId);
        return reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.PUBLISHED, pageable);
    }

    @Transactional(readOnly = true)
    public ProductRatingSummaryResponse getRatingSummary(Long productId) {
        requireProduct(productId);
        Long count = reviewRepository.countPublishedByProductId(productId);
        Double average = reviewRepository.averagePublishedRatingByProductId(productId);
        BigDecimal roundedAverage = BigDecimal.valueOf(average == null ? 0.0 : average)
                .setScale(2, RoundingMode.HALF_UP);
        return new ProductRatingSummaryResponse(productId, roundedAverage, count);
    }

    public Review createReview(Long productId, Integer rating, String title, String body, Long orderId) {
        User user = accessControlService.currentUser();
        Product product = requireProduct(productId);
        ensureUserCanCreateReview(user, productId);

        Order order = findOrderForReview(orderId, user);

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setOrder(order);
        review.setRating(rating);
        review.setTitle(title);
        review.setBody(body);
        review.setStatus(ReviewStatus.PUBLISHED);
        return reviewRepository.save(review);
    }

    public Review updateReview(Long reviewId, Integer rating, String title, String body) {
        Review review = requireReview(reviewId);
        requireReviewOwner(review);
        if (review.getStatus() == ReviewStatus.DELETED) {
            throw new BusinessRuleException("Deleted review cannot be updated");
        }
        review.setRating(rating);
        review.setTitle(title);
        review.setBody(body);
        review.setStatus(ReviewStatus.PUBLISHED);
        return reviewRepository.save(review);
    }

    public void deleteOwnReview(Long reviewId) {
        Review review = requireReview(reviewId);
        requireReviewOwner(review);
        review.setStatus(ReviewStatus.DELETED);
        reviewRepository.save(review);
    }

    @Transactional(readOnly = true)
    public Page<Review> getAdminReviews(ReviewStatus status, Long productId, Pageable pageable) {
        if (status != null) {
            return reviewRepository.findByStatus(status, pageable);
        }
        if (productId != null) {
            return reviewRepository.findByProductId(productId, pageable);
        }
        return reviewRepository.findAll(pageable);
    }

    public Review updateStatus(Long reviewId, ReviewStatus status) {
        if (status == ReviewStatus.DELETED) {
            throw new BusinessRuleException("Use DELETE to delete a review");
        }
        Review review = requireReview(reviewId);
        review.setStatus(status);
        return reviewRepository.save(review);
    }

    public void deleteAdminReview(Long reviewId) {
        Review review = requireReview(reviewId);
        review.setStatus(ReviewStatus.DELETED);
        reviewRepository.save(review);
    }

    private Product requireProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + productId + " not found"));
    }

    private Review requireReview(Long reviewId) {
        return reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review with id " + reviewId + " not found"));
    }

    private void requireReviewOwner(Review review) {
        User current = accessControlService.currentUser();
        if (review.getUser() == null || !review.getUser().getId().equals(current.getId())) {
            throw new ForbiddenOperationException("Access denied to this review");
        }
    }

    private void ensureUserCanCreateReview(User user, Long productId) {
        if (reviewRepository.findByUserIdAndProductId(user.getId(), productId).isPresent()) {
            throw new ConflictException("You already reviewed this product");
        }
    }

    private Order findOrderForReview(Long orderId, User user) {
        if (orderId == null) {
            return null;
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));
        if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
            throw new ForbiddenOperationException("Access denied to this order");
        }
        return order;
    }
}
