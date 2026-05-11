package com.example.shopupu.catalog.controller;

import com.example.shopupu.catalog.dto.CategoryResponse;
import com.example.shopupu.catalog.dto.ProductResponse;
import com.example.shopupu.catalog.mapper.CatalogMapper;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.service.CatalogService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
/**
 * describes the CatalogController class.
 */
public class CatalogController {

    private final CatalogService catalogService;
    private final CategoryRepository categoryRepository;
    private final CatalogMapper catalogMapper;

    // handles CatalogController.
    public CatalogController(CatalogService catalogService,
                             CategoryRepository categoryRepository,
                             CatalogMapper catalogMapper) {
        this.catalogService = catalogService;
        this.categoryRepository = categoryRepository;
        this.catalogMapper = catalogMapper;
    }

    @GetMapping("/categories")
    // handles listCategories.
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(catalogMapper::toCategoryResponse)
                .toList();
    }

    @GetMapping("/categories/{slug}")
    // handles getBySlug.
    public ResponseEntity<CategoryResponse> getBySlug(@PathVariable String slug) {
        return categoryRepository.findBySlug(slug)
                .map(catalogMapper::toCategoryResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/products")
    // handles getAllProducts.
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> products = catalogService.getAllProducts().stream()
                .map(catalogMapper::toProductResponse)
                .toList();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/products/{id}")
    // handles getProduct.
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(catalogMapper.toProductResponse(catalogService.getProduct(id)));
    }

    @GetMapping("/categories/{slug}/products")
    // handles getProductsByCategory.
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable String slug) {
        List<ProductResponse> products = catalogService.getProductsByCategory(slug).stream()
                .map(catalogMapper::toProductResponse)
                .toList();
        return ResponseEntity.ok(products);
    }
}
