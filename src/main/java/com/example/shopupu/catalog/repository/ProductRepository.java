package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySku(String sku);

    List<Product> findAll();
    List<Product> findByCategory_Slug(String slug);
    List<Product> findByCategoryAndEnabledIsTrue(Category category);
}
