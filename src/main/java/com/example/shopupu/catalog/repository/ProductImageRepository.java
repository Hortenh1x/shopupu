package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.ProductImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * describes the ProductImageRepository interface.
 */
public interface ProductImageRepository extends JpaRepository<ProductImage, Long> {

    List<ProductImage> findByProductIdOrderByPositionAsc(Long productId);

    Optional<ProductImage> findByIdAndProductId(Long id, Long productId);
}
