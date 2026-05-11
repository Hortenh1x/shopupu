package com.example.shopupu.reviews.repository;

import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

/**
 * describes the ReviewRepository interface.
 */
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);

    Page<Review> findByProductId(Long productId, Pageable pageable);

    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    @Query("""
            select count(r)
            from Review r
            where r.product.id = :productId and r.status = com.example.shopupu.reviews.entity.ReviewStatus.PUBLISHED
            """)
    Long countPublishedByProductId(Long productId);

    @Query("""
            select coalesce(avg(r.rating), 0)
            from Review r
            where r.product.id = :productId and r.status = com.example.shopupu.reviews.entity.ReviewStatus.PUBLISHED
            """)
    Double averagePublishedRatingByProductId(Long productId);
}
