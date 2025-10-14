package com.example.shopupu.catalog.controller;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.catalog.service.ProductQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/catalog")
public class CatalogQueryController {

    private final ProductQueryService productQueryService;
    /**
     * Листинг товаров с фильтрами:
     * Примеры:
     *   /api/catalog/products/search?q=iphone&minPrice=500&maxPrice=1500&categoryId=1&enabled=true&page=0&size=12&sort=price,desc
     *   /api/catalog/products/search?sort=createdAt,desc
     */
    @GetMapping("products/search")
    public Page<Product> searchProducts(
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
        filter.enabled = enabled;

        return productQueryService.findProducts(filter, pageable);
    }
}
