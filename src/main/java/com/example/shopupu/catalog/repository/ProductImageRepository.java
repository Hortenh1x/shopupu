package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * describes the ProductImageRepository interface.
 */
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByPositionAsc(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);
}
