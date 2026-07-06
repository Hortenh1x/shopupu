package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.Brand;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {
    Optional<Brand> findBySlug(String slug);

    Optional<Brand> findByNameIgnoreCase(String name);

    boolean existsBySlug(String slug);
}
