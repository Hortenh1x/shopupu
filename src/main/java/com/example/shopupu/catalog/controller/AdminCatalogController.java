package com.example.shopupu.catalog.controller;

import com.example.shopupu.catalog.dto.CategoryRequest;
import com.example.shopupu.catalog.dto.CategoryResponse;
import com.example.shopupu.catalog.dto.ProductImageResponse;
import com.example.shopupu.catalog.dto.ProductRequest;
import com.example.shopupu.catalog.dto.ProductResponse;
import com.example.shopupu.catalog.dto.VariantRequest;
import com.example.shopupu.catalog.dto.VariantResponse;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.catalog.mapper.CatalogMapper;
import com.example.shopupu.catalog.service.CatalogService;
import com.example.shopupu.inventory.service.InventoryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/catalog")
@PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
public class AdminCatalogController {

    private final CatalogService catalogService;
    private final CatalogMapper catalogMapper;
    private final InventoryService inventoryService;

    @PostMapping("/categories")
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        var created = catalogService.createCategory(request.name(), request.slug(), request.description(), request.parentId());
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogMapper.toCategoryResponse(created));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        var updated = catalogService.updateCategory(id, request.name(), request.slug(), request.description(), request.parentId());
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(updated));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        catalogService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/products")
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        var created = catalogService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogMapper.toProductResponse(created, Map.of()));
    }

    @GetMapping("/products")
    public ResponseEntity<Page<ProductResponse>> getProducts(@PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(catalogService.getAllProductsForAdmin(pageable)
                .map(product -> catalogMapper.toProductResponse(product, availability(product))));
    }

    @GetMapping("/products/{id}")
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        Product product = catalogService.getProductForAdmin(id);
        return ResponseEntity.ok(catalogMapper.toProductResponse(product, availability(product)));
    }

    @PutMapping("/products/{id}")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        var updated = catalogService.updateProduct(id, request);
        return ResponseEntity.ok(catalogMapper.toProductResponse(updated, availability(updated)));
    }

    @DeleteMapping("/products/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        catalogService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    // === Variants ===========================================================

    @GetMapping("/products/{id}/variants")
    public ResponseEntity<List<VariantResponse>> getVariants(@PathVariable Long id) {
        List<ProductVariant> variants = catalogService.getVariants(id);
        Map<Long, Integer> availability = inventoryService.availabilityFor(
                variants.stream().map(ProductVariant::getId).toList());
        return ResponseEntity.ok(variants.stream()
                .map(v -> catalogMapper.toVariantResponse(v, availability.getOrDefault(v.getId(), 0)))
                .toList());
    }

    @PostMapping("/products/{id}/variants")
    public ResponseEntity<VariantResponse> addVariant(
            @PathVariable Long id,
            @Valid @RequestBody VariantRequest request
    ) {
        ProductVariant variant = catalogService.addVariant(id, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(catalogMapper.toVariantResponse(variant, inventoryService.availableFor(variant.getId())));
    }

    @PutMapping("/variants/{variantId}")
    public ResponseEntity<VariantResponse> updateVariant(
            @PathVariable Long variantId,
            @Valid @RequestBody VariantRequest request
    ) {
        ProductVariant variant = catalogService.updateVariant(variantId, request);
        return ResponseEntity.ok(
                catalogMapper.toVariantResponse(variant, inventoryService.availableFor(variant.getId())));
    }

    @DeleteMapping("/variants/{variantId}")
    public ResponseEntity<Void> deleteVariant(@PathVariable Long variantId) {
        catalogService.deleteVariant(variantId);
        return ResponseEntity.noContent().build();
    }

    // === Images =============================================================

    @PostMapping("/products/{id}/images")
    public ResponseEntity<ProductImageResponse> uploadProductImage(
            @PathVariable Long id,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String altText,
            @RequestParam(required = false) Integer sortOrder
    ) {
        var image = catalogService.addProductImage(id, file, altText, sortOrder);
        return ResponseEntity.status(HttpStatus.CREATED).body(catalogMapper.toProductImageResponse(image));
    }

    @DeleteMapping("/products/{productId}/images/{imageId}")
    public ResponseEntity<Void> deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        catalogService.deleteProductImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }

    private Map<Long, Integer> availability(Product product) {
        if (product.getVariants() == null || product.getVariants().isEmpty()) {
            return Map.of();
        }
        return inventoryService.availabilityFor(
                product.getVariants().stream().map(ProductVariant::getId).toList());
    }
}
