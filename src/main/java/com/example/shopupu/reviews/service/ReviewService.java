package com.example.shopupu.reviews.service;

import com.example.shopupu.ai.event.ProductReviewsChangedEvent;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ConflictException;
import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.security.AccessControlService;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.reviews.dto.ProductRatingSummaryResponse;
import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.repository.ReviewRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class ReviewService {

    /** Order states that prove the user actually bought the product (REV-01). */
    private static final Set<OrderStatus> PURCHASED_STATES = EnumSet.of(
            OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.SHIPPED,
            OrderStatus.DELIVERED, OrderStatus.COMPLETED);

    private final ReviewRepository reviewRepository;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final AccessControlService accessControlService;
    private final com.example.shopupu.common.audit.AuditService auditService;
    // AI review summaries regenerate AFTER_COMMIT when the approved set changes
    private final ApplicationEventPublisher eventPublisher;

    @Transactional(readOnly = true)
    public Page<Review> getPublishedReviews(Long productId, Pageable pageable) {
        requireProduct(productId);
        return reviewRepository.findByProductIdAndStatus(productId, ReviewStatus.APPROVED, pageable);
    }

    @org.springframework.cache.annotation.Cacheable(cacheNames = "productRating", key = "#productId")
    @Transactional(readOnly = true)
    public ProductRatingSummaryResponse getRatingSummary(Long productId) {
        requireProduct(productId);
        Long count = reviewRepository.countPublishedByProductId(productId);
        Double average = reviewRepository.averagePublishedRatingByProductId(productId);
        BigDecimal roundedAverage = BigDecimal.valueOf(average == null ? 0.0 : average)
                .setScale(2, RoundingMode.HALF_UP);
        return new ProductRatingSummaryResponse(productId, roundedAverage, count);
    }

    public Review createReview(Long productId, Integer rating, String body, Long orderId) {
        User user = accessControlService.currentUser();
        Product product = requireProduct(productId);
        ensureUserCanCreateReview(user, productId);
        ensureVerifiedPurchase(user, productId, orderId);

        Review review = new Review();
        review.setUser(user);
        review.setProduct(product);
        review.setOrder(orderId == null ? null : orderRepository.findById(orderId).orElse(null));
        review.setRating(rating);
        review.setBody(sanitize(body));
        // moderation first: reviews appear publicly only after approval (REV-02)
        review.setStatus(ReviewStatus.PENDING);
        return reviewRepository.save(review);
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "productRating", allEntries = true)
    public Review updateReview(Long reviewId, Integer rating, String body) {
        Review review = requireReview(reviewId);
        requireReviewOwner(review);
        if (review.getStatus() == ReviewStatus.DELETED) {
            throw new BusinessRuleException("Deleted review cannot be updated");
        }
        ReviewStatus previous = review.getStatus();
        review.setRating(rating);
        review.setBody(sanitize(body));
        // edits go back through moderation instead of self-publishing
        review.setStatus(ReviewStatus.PENDING);
        Review saved = reviewRepository.save(review);
        publishIfApprovedSetChanged(saved, previous);
        return saved;
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "productRating", allEntries = true)
    public void deleteOwnReview(Long reviewId) {
        Review review = requireReview(reviewId);
        requireReviewOwner(review);
        ReviewStatus previous = review.getStatus();
        review.setStatus(ReviewStatus.DELETED);
        reviewRepository.save(review);
        publishIfApprovedSetChanged(review, previous);
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

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "productRating", allEntries = true)
    public Review updateStatus(Long reviewId, ReviewStatus status) {
        if (status == ReviewStatus.DELETED) {
            throw new BusinessRuleException("Use DELETE to delete a review");
        }
        Review review = requireReview(reviewId);
        ReviewStatus previous = review.getStatus();
        review.setStatus(status);
        Review saved = reviewRepository.save(review);
        auditService.record(accessControlService.currentEmail(), "REVIEW_MODERATED",
                "review", String.valueOf(reviewId), "-> " + status);
        publishIfApprovedSetChanged(saved, previous);
        return saved;
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "productRating", allEntries = true)
    public void deleteAdminReview(Long reviewId) {
        Review review = requireReview(reviewId);
        ReviewStatus previous = review.getStatus();
        review.setStatus(ReviewStatus.DELETED);
        reviewRepository.save(review);
        publishIfApprovedSetChanged(review, previous);
    }

    /** The AI summary only cares about transitions into or out of APPROVED. */
    private void publishIfApprovedSetChanged(Review review, ReviewStatus previous) {
        if (previous == ReviewStatus.APPROVED || review.getStatus() == ReviewStatus.APPROVED) {
            eventPublisher.publishEvent(new ProductReviewsChangedEvent(review.getProduct().getId()));
        }
    }

    /** Strips all HTML from user content (REV-03/SEC-07). */
    private String sanitize(String input) {
        if (input == null) {
            return null;
        }
        return Jsoup.clean(input, Safelist.none()).trim();
    }

    private void ensureVerifiedPurchase(User user, Long productId, Long orderId) {
        if (orderId != null) {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order with id " + orderId + " not found"));
            if (order.getUser() == null || !order.getUser().getId().equals(user.getId())) {
                throw new ForbiddenOperationException("Access denied to this order");
            }
            boolean containsProduct = order.getItems().stream()
                    .anyMatch(item -> productId.equals(item.getProductId()));
            if (!containsProduct || !PURCHASED_STATES.contains(order.getStatus())) {
                throw new BusinessRuleException("Review requires a paid order containing this product");
            }
            return;
        }
        boolean purchased = orderRepository.existsByUserAndStatusInAndItems_ProductId(
                user, PURCHASED_STATES, productId);
        if (!purchased) {
            throw new BusinessRuleException("Only verified buyers can review this product");
        }
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
}
