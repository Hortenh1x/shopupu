package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);

    Page<Product> findByEnabledIsTrueAndDeletedAtIsNull(Pageable pageable);

    Page<Product> findByCategory_SlugAndEnabledIsTrueAndDeletedAtIsNull(String slug, Pageable pageable);
}
