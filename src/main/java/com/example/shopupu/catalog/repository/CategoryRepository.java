package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    // Найти категорию по slug (удобно для REST /categories/{slug})
    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);
}