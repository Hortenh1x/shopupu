package com.example.shopupu.reviews.repository;

import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    // to-one graph is safe with pagination; needed because admin mapping
    // happens in the controller and OSIV is off
    @Override
    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findByProductIdAndStatus(Long productId, ReviewStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findByStatus(ReviewStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"user", "product"})
    Page<Review> findByProductId(Long productId, Pageable pageable);

    Optional<Review> findByUserIdAndProductId(Long userId, Long productId);

    @EntityGraph(attributePaths = {"product"})
    java.util.List<Review> findByUserId(Long userId);

    @Query("""
            select count(r)
            from Review r
            where r.product.id = :productId and r.status = com.example.shopupu.reviews.entity.ReviewStatus.APPROVED
            """)
    Long countPublishedByProductId(Long productId);

    @Query("""
            select coalesce(avg(r.rating), 0)
            from Review r
            where r.product.id = :productId and r.status = com.example.shopupu.reviews.entity.ReviewStatus.APPROVED
            """)
    Double averagePublishedRatingByProductId(Long productId);
}
