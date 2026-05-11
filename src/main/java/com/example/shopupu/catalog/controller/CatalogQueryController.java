package com.example.shopupu.catalog.controller;

import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.mapper.CatalogMapper;
import com.example.shopupu.catalog.service.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/catalog")
/**
 * describes the CatalogQueryController class.
 */
public class CatalogQueryController {

    private final ProductQueryService productQueryService;
    private final CatalogMapper catalogMapper;

    @GetMapping("/products/search")
    // handles searchProducts.
    public Page<ProductListItem> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Boolean enabled,
            Pageable pageable
    ) {
        var filter = new ProductFilter();
        filter.q = q;
        filter.categoryId = categoryId;
        filter.minPrice = minPrice;
        filter.maxPrice = maxPrice;
        filter.enabled = enabled == null ? Boolean.TRUE : enabled;

        return productQueryService.findProducts(filter, pageable)
                .map(catalogMapper::toProductListItem);
    }
}