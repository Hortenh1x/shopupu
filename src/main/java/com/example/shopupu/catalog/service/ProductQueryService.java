package com.example.shopupu.catalog.service;

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
    private final com.example.shopupu.catalog.mapper.CatalogMapper catalogMapper;

    // mapped inside the transaction: preview image is a lazy collection (OSIV off)
    @Transactional(readOnly = true)
    public Page<com.example.shopupu.catalog.dto.ProductListItem> findProducts(ProductFilter f, Pageable pageable) {
        return productRepository.findAll(ProductSpecifications.build(f), pageable)
                .map(catalogMapper::toProductListItem);
    }
}
