package com.example.shopupu.catalog.service;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.catalog.repository.ProductSpecifications;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    /**
     * Loads products by id keeping the given (relevance) order — used by
     * semantic search and recommendations. Sellability is re-checked here in
     * case state changed after the candidate ids were selected.
     */
    @Transactional(readOnly = true)
    public List<com.example.shopupu.catalog.dto.ProductListItem> findListItemsByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        Map<Long, Product> byId = productRepository.findAllById(ids).stream()
                .filter(p -> Boolean.TRUE.equals(p.getEnabled()) && !p.isDeleted())
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        return ids.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(catalogMapper::toProductListItem)
                .toList();
    }
}
