package com.example.shopupu.catalog.controller;

import com.example.shopupu.catalog.dto.BrandResponse;
import com.example.shopupu.catalog.dto.CategoryResponse;
import com.example.shopupu.catalog.dto.ProductListItem;
import com.example.shopupu.catalog.dto.ProductResponse;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.catalog.mapper.CatalogMapper;
import com.example.shopupu.catalog.service.CatalogService;
import com.example.shopupu.inventory.service.InventoryService;
import java.util.List;
import java.util.Map;
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
    private final InventoryService inventoryService;

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
        return ResponseEntity.ok(catalogService.getAllProducts(pageable)
                .map(catalogMapper::toProductListItem));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        Product product = catalogService.getProduct(id);
        return ResponseEntity.ok(catalogMapper.toProductResponse(product, availability(product)));
    }

    @GetMapping("/categories/{slug}/products")
    public ResponseEntity<Page<ProductListItem>> getProductsByCategory(
            @PathVariable String slug,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(catalogService.getProductsByCategory(slug, pageable)
                .map(catalogMapper::toProductListItem));
    }

    private Map<Long, Integer> availability(Product product) {
        List<Long> variantIds = product.getVariants().stream().map(ProductVariant::getId).toList();
        return inventoryService.availabilityFor(variantIds);
    }
}
