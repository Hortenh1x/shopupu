package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.ProductVariant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, Long> {

    Optional<ProductVariant> findBySku(String sku);

    List<ProductVariant> findByProduct_Id(Long productId);

    @EntityGraph(attributePaths = {"product", "product.brand"})
    Optional<ProductVariant> findWithProductById(Long id);

    boolean existsBySku(String sku);
}
