package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * describes the CategoryRepository interface.
 */
public interface CategoryRepository extends JpaRepository<Category, Long> {


    Optional<Category> findBySlug(String slug);

    boolean existsBySlug(String slug);
}