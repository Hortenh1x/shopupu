package com.example.shopupu.catalog.controller;

import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.service.CatalogService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogService catalogService;
    private final CategoryRepository categoryRepository;

    public CatalogController(CatalogService catalogService, CategoryRepository categoryRepository) {
        this.catalogService = catalogService;
        this.categoryRepository = categoryRepository;
    }

    // -------- Категории --------

    @PostMapping("/categories")
    public ResponseEntity<Category> createCategory(@RequestBody CreateCategoryRequest req) {
        var created = catalogService.createCategory(req.name(), req.slug(), req.description(), req.parentId());
        return ResponseEntity.ok(created);
    }

    @GetMapping("/categories")
    public List<Category> listCategories() {
        return categoryRepository.findAll();
    }

    @GetMapping("/categories/{slug}")
    public ResponseEntity<Category> getBySlug(@PathVariable String slug) {
        return categoryRepository.findBySlug(slug)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // -------- Товары --------

    @PostMapping("/products")
    public ResponseEntity<Product> createProduct(@RequestBody CreateProductRequest req) {
        var created = catalogService.createProduct(req.categoryId(), req.title(), req.description(), req.price(), req.sku(), req.stock());
        return ResponseEntity.ok(created);
    }

    @GetMapping("/products")
    public ResponseEntity<List<Product>> getAllProducts() {
        List<Product> products = catalogService.getAllProducts();
        return ResponseEntity.ok(products);
    }

    @GetMapping("/categories/{slug}/products")
    public ResponseEntity<List<Product>> getProductsByCategory(@PathVariable String slug) {
        List<Product> products = catalogService.getProductsByCategory(slug);
        return ResponseEntity.ok(products);
    }


    // DTO-рекорды для входящих тел — простые и понятные

    public record CreateCategoryRequest(
            @NotBlank String name,
            @NotBlank String slug,
            String description,
            Long parentId
    ) {}

    public record CreateProductRequest(
            @NotNull Long categoryId,
            @NotBlank String title,
            String description,
            @NotNull BigDecimal price,
            @NotBlank String sku,
            @NotNull Integer stock
    ) {}

}