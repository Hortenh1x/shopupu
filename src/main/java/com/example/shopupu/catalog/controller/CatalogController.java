package com.example.shopupu.catalog.controller;

import com.example.shopupu.catalog.dto.BrandResponse;
import com.example.shopupu.catalog.dto.CategoryResponse;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.dto.ProductResponse;
import com.example.shopupu.catalog.mapper.CatalogMapper;
import com.example.shopupu.catalog.service.CatalogService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;
    private final CatalogMapper catalogMapper;

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() {
        return catalogService.getAllCategories().stream()
                .map(catalogMapper::toCategoryResponse)
                .toList();
    }

    @GetMapping("/categories/{slug}")
    public ResponseEntity<CategoryResponse> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(catalogService.getCategoryBySlug(slug)));
    }

    @GetMapping("/brands")
    public List<BrandResponse> listBrands() {
        return catalogService.getAllBrands().stream()
                .map(catalogMapper::toBrandResponse)
                .toList();
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductListItem>> getAllProducts(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(catalogService.getAllProducts(pageable));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(catalogService.getProduct(id));
    }

    @GetMapping("/categories/{slug}/products")
    public ResponseEntity<Page<ProductListItem>> getProductsByCategory(
            @PathVariable String slug,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(catalogService.getProductsByCategory(slug, pageable));
    }
}
