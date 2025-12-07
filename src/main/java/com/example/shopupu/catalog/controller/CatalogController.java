package com.example.shopupu.catalog.controller;

import com.example.shopupu.catalog.dto.CategoryRequest;
import com.example.shopupu.catalog.dto.CategoryResponse;
import com.example.shopupu.catalog.dto.ProductRequest;
import com.example.shopupu.catalog.dto.ProductResponse;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.mapper.CatalogMapper;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.service.CatalogService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;
    private final CategoryRepository categoryRepository;
    private final CatalogMapper catalogMapper;

    public CatalogController(CatalogService catalogService,
                             CategoryRepository categoryRepository,
                             CatalogMapper catalogMapper) {
        this.catalogService = catalogService;
        this.categoryRepository = categoryRepository;
        this.catalogMapper = catalogMapper;
    }

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest req) {
        Category created = catalogService.createCategory(req.name(), req.slug(), req.description(), req.parentId());
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(created));
    }

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAll().stream()
                .map(catalogMapper::toCategoryResponse)
                .toList();
    }

    @GetMapping("/categories/{slug}")
    public ResponseEntity<CategoryResponse> getBySlug(@PathVariable String slug) {
        return categoryRepository.findBySlug(slug)
                .map(catalogMapper::toCategoryResponse)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest req) {
        var created = catalogService.createProduct(
                req.categoryId(),
                req.title(),
                req.description(),
                req.price(),
                req.sku(),
                req.stock(),
                req.enabled()
        );
        return ResponseEntity.ok(catalogMapper.toProductResponse(created));
    }

    @GetMapping("/products")
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> products = catalogService.getAllProducts().stream()
                .map(catalogMapper::toProductResponse)
                .toList();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/categories/{slug}/products")
    public ResponseEntity<List<ProductResponse>> getProductsByCategory(@PathVariable String slug) {
        List<ProductResponse> products = catalogService.getProductsByCategory(slug).stream()
                .map(catalogMapper::toProductResponse)
                .toList();
        return ResponseEntity.ok(products);
    }
}
