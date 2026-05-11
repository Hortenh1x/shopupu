package com.example.shopupu.catalog.controller;

import com.example.shopupu.catalog.dto.CategoryRequest;
import com.example.shopupu.catalog.dto.CategoryResponse;
import com.example.shopupu.catalog.dto.ProductImageResponse;
import com.example.shopupu.catalog.dto.ProductRequest;
import com.example.shopupu.catalog.dto.ProductResponse;
import com.example.shopupu.catalog.mapper.CatalogMapper;
import com.example.shopupu.catalog.service.CatalogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/catalog")
@PreAuthorize("hasRole('ADMIN')")
/**
 * describes the AdminCatalogController class.
 */
public class AdminCatalogController {

    private final CatalogService catalogService;
    private final CatalogMapper catalogMapper;

    @PostMapping("/categories")
    // handles createCategory.
    public ResponseEntity<CategoryResponse> createCategory(@Valid @RequestBody CategoryRequest request) {
        var created = catalogService.createCategory(request.name(), request.slug(), request.description(), request.parentId());
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(created));
    }

    @PutMapping("/categories/{id}")
    // handles updateCategory.
    public ResponseEntity<CategoryResponse> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        var updated = catalogService.updateCategory(id, request.name(), request.slug(), request.description(), request.parentId());
        return ResponseEntity.ok(catalogMapper.toCategoryResponse(updated));
    }

    @DeleteMapping("/categories/{id}")
    // handles deleteCategory.
    public ResponseEntity<Void> deleteCategory(@PathVariable Long id) {
        catalogService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/products")
    // handles createProduct.
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        var created = catalogService.createProduct(
                request.categoryId(),
                request.title(),
                request.description(),
                request.price(),
                request.sku(),
                request.stock(),
                request.enabled()
        );
        return ResponseEntity.ok(catalogMapper.toProductResponse(created));
    }

    @GetMapping("/products")
    // handles getProducts.
    public ResponseEntity<List<ProductResponse>> getProducts() {
        return ResponseEntity.ok(catalogService.getAllProductsForAdmin().stream()
                .map(catalogMapper::toProductResponse)
                .toList());
    }

    @GetMapping("/products/{id}")
    // handles getProduct.
    public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
        return ResponseEntity.ok(catalogMapper.toProductResponse(catalogService.getProductForAdmin(id)));
    }

    @PutMapping("/products/{id}")
    // handles updateProduct.
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody ProductRequest request
    ) {
        var updated = catalogService.updateProduct(
                id,
                request.categoryId(),
                request.title(),
                request.description(),
                request.price(),
                request.sku(),
                request.stock(),
                request.enabled()
        );
        return ResponseEntity.ok(catalogMapper.toProductResponse(updated));
    }

    @DeleteMapping("/products/{id}")
    // handles deleteProduct.
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        catalogService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/products/{id}/images")
    // handles uploadProductImage.
    public ResponseEntity<ProductImageResponse> uploadProductImage(
            @PathVariable Long id,
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String altText,
            @RequestParam(required = false) Integer sortOrder
    ) {
        var image = catalogService.addProductImage(id, file, altText, sortOrder);
        return ResponseEntity.ok(catalogMapper.toProductImageResponse(image));
    }

    @DeleteMapping("/products/{productId}/images/{imageId}")
    public ResponseEntity<Void> deleteProductImage(
            @PathVariable Long productId,
            @PathVariable Long imageId
    ) {
        catalogService.deleteProductImage(productId, imageId);
        return ResponseEntity.noContent().build();
    }
}
