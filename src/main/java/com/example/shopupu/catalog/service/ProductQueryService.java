package com.example.shopupu.catalog.service;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.catalog.repository.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductQueryService {

    private final ProductRepository productRepository;
    /**
     * Главный метод листинга: принимает фильтр и Pageable.
     * Pageable включает page/size/sort — Spring сам соберёт из query params.
     */
    @Transactional(readOnly = true)
    public Page<Product> findProducts(ProductFilter f, Pageable pageable) {
        var spec = ProductSpecifications.build(
                f.q, f.categoryId, f.minPrice, f.maxPrice, f.enabled
        );
        return productRepository.findAll(spec, pageable);
    }
}