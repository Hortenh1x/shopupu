package com.example.shopupu.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.catalog.dto.ProductRequest;
import com.example.shopupu.catalog.dto.VariantRequest;
import com.example.shopupu.catalog.entity.Brand;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Gender;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.catalog.repository.BrandRepository;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.repository.ProductImageRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.catalog.repository.ProductVariantRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ConflictException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.common.storage.FileStorageService;
import com.example.shopupu.inventory.service.InventoryService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

/**
 * describes the CatalogServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ProductImageRepository productImageRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @Mock
    private BrandRepository brandRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private FileStorageService fileStorageService;

    @org.mockito.Spy
    private com.example.shopupu.catalog.mapper.CatalogMapper catalogMapper = new com.example.shopupu.catalog.mapper.CatalogMapper();

    @InjectMocks
    private CatalogService catalogService;

    // === Categories =========================================================

    // handles createCategory.
    @Test
    void createCategorySavesCategoryWithoutParent() {
        when(categoryRepository.existsBySlug("phones")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Category category = catalogService.createCategory("Phones", "phones", "Mobile phones", null);

        assertEquals("Phones", category.getName());
        assertEquals("phones", category.getSlug());
        verify(categoryRepository).save(category);
    }

    // handles createCategory.
    @Test
    void createCategoryRejectsDuplicateSlugAndMissingParent() {
        when(categoryRepository.existsBySlug("phones")).thenReturn(true);
        assertThrows(ConflictException.class, () -> catalogService.createCategory("Phones", "phones", null, null));

        when(categoryRepository.existsBySlug("child")).thenReturn(false);
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> catalogService.createCategory("Child", "child", null, 99L));
    }

    // handles updateCategory.
    @Test
    void updateCategoryChangesFieldsAndParent() {
        Category category = category(1L, "Old", "old", null);
        Category parent = category(2L, "Parent", "parent", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findBySlug("new")).thenReturn(Optional.empty());
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(parent));
        when(categoryRepository.save(category)).thenReturn(category);

        Category updated = catalogService.updateCategory(1L, "New", "new", "desc", 2L);

        assertEquals("New", updated.getName());
        assertEquals("new", updated.getSlug());
        assertSame(parent, updated.getParent());
    }

    // handles updateCategory.
    @Test
    void updateCategoryRejectsDuplicateSlugSelfParentAndCycles() {
        Category category = category(1L, "Old", "old", null);
        Category duplicate = category(2L, "Duplicate", "new", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.findBySlug("new")).thenReturn(Optional.of(duplicate));
        assertThrows(ConflictException.class, () -> catalogService.updateCategory(1L, "New", "new", null, null));

        when(categoryRepository.findBySlug("same")).thenReturn(Optional.empty());
        assertThrows(BusinessRuleException.class, () -> catalogService.updateCategory(1L, "Same", "same", null, 1L));

        Category child = category(3L, "Child", "child", category);
        when(categoryRepository.findBySlug("cycle")).thenReturn(Optional.empty());
        when(categoryRepository.findById(3L)).thenReturn(Optional.of(child));
        assertThrows(BusinessRuleException.class, () -> catalogService.updateCategory(1L, "Cycle", "cycle", null, 3L));
    }

    // handles deleteCategory.
    @Test
    void deleteCategoryDeletesExistingCategory() {
        Category category = category(1L, "Phones", "phones", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        catalogService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    // === Products ===========================================================

    // handles createProduct.
    @Test
    void createProductSavesProductWithCategory() {
        Category category = category(1L, "Phones", "phones", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.findBySlug("phone-x")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductRequest request = new ProductRequest(1L, "Phone X", "phone-x", "desc",
                new BigDecimal("10.00"), null, null, null, null, null, null, null, null, false);

        var product = catalogService.createProduct(request);

        assertEquals("Phone X", product.title());
        assertEquals("phone-x", product.slug());
        assertEquals(new BigDecimal("10.00"), product.price());
        assertEquals(false, product.enabled());
        assertEquals(Gender.UNISEX, product.gender());
        assertNull(product.brandName());
        assertEquals(category.getId(), product.categoryId());
    }

    // handles createProduct.
    @Test
    void createProductGeneratesSlugFromTitleAndCreatesMissingBrand() {
        Category category = category(1L, "Hoodies", "hoodies", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(brandRepository.findByNameIgnoreCase("Nike")).thenReturn(Optional.empty());
        when(brandRepository.existsBySlug("nike")).thenReturn(false);
        when(brandRepository.save(any(Brand.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productRepository.findBySlug("cool-hoodie")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductRequest request = new ProductRequest(1L, "Cool Hoodie", null, "desc",
                new BigDecimal("59.99"), null, "Nike", Gender.MEN, null, null, null, null, null, true);

        var product = catalogService.createProduct(request);

        assertEquals("cool-hoodie", product.slug());
        assertEquals(Gender.MEN, product.gender());
        assertEquals("Nike", product.brandName());

        ArgumentCaptor<Brand> brandCaptor = ArgumentCaptor.forClass(Brand.class);
        verify(brandRepository).save(brandCaptor.capture());
        assertEquals("Nike", brandCaptor.getValue().getName());
        assertEquals("nike", brandCaptor.getValue().getSlug());
    }

    // handles createProduct.
    @Test
    void createProductReusesExistingBrand() {
        Category category = category(1L, "Hoodies", "hoodies", null);
        Brand existing = new Brand("Nike", "nike");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(brandRepository.findByNameIgnoreCase("Nike")).thenReturn(Optional.of(existing));
        when(productRepository.findBySlug("cool-hoodie")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductRequest request = new ProductRequest(1L, "Cool Hoodie", null, null,
                new BigDecimal("59.99"), null, "Nike", null, null, null, null, null, null, true);

        var product = catalogService.createProduct(request);

        assertEquals("Nike", product.brandName());
        verify(brandRepository, never()).save(any(Brand.class));
    }

    // handles createProduct.
    @Test
    void createProductRejectsMissingCategory() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        ProductRequest request = new ProductRequest(1L, "Phone", null, null,
                BigDecimal.ONE, null, null, null, null, null, null, null, null, true);

        assertThrows(ResourceNotFoundException.class, () -> catalogService.createProduct(request));
        verify(productRepository, never()).save(any(Product.class));
    }

    // handles updateProduct.
    @Test
    void updateProductChangesFields() {
        Category oldCategory = category(1L, "Old", "old", null);
        Category newCategory = category(2L, "New", "new", null);
        Product product = product(10L, oldCategory);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(newCategory));
        when(productRepository.save(product)).thenReturn(product);

        ProductRequest request = new ProductRequest(2L, "Updated", null, "new desc",
                new BigDecimal("20.00"), new BigDecimal("25.00"), null, Gender.WOMEN,
                "summer", "cotton", "wash cold", "meta", "meta desc", false);

        var updated = catalogService.updateProduct(10L, request);

        assertEquals("Updated", updated.title());
        assertEquals(new BigDecimal("20.00"), updated.price());
        assertEquals(new BigDecimal("25.00"), updated.oldPrice());
        assertEquals(Gender.WOMEN, updated.gender());
        assertEquals("summer", updated.season());
        assertEquals("cotton", updated.material());
        assertEquals(false, updated.enabled());
        // slug is not touched when the request does not provide one
        assertEquals("phone", updated.slug());
        assertEquals(newCategory.getId(), updated.categoryId());
    }

    // handles getAllProducts.
    @Test
    void getAllProductsReturnsOnlyEnabledNotDeletedProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        Product product = product(1L, category(1L, "Phones", "phones", null));
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
        when(productRepository.findByEnabledIsTrueAndDeletedAtIsNull(pageable)).thenReturn(page);

        var result = catalogService.getAllProducts(pageable);
        assertEquals(1, result.getTotalElements());
        assertEquals("Phone", result.getContent().get(0).title());
    }

    // handles getProductsByCategory.
    @Test
    void getProductsByCategoryReturnsOnlyEnabledNotDeletedCategoryProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        Product product = product(1L, category(1L, "Phones", "phones", null));
        Page<Product> page = new PageImpl<>(List.of(product), pageable, 1);
        when(productRepository.findByCategory_SlugAndEnabledIsTrueAndDeletedAtIsNull("phones", pageable)).thenReturn(page);

        var result = catalogService.getProductsByCategory("phones", pageable);
        assertEquals(1, result.getTotalElements());
        assertEquals("Phone", result.getContent().get(0).title());
    }

    // handles getProduct.
    @Test
    void getProductThrowsNotFoundForDeletedOrDisabledProduct() {
        Product deleted = product(5L, category(1L, "Phones", "phones", null));
        deleted.setDeletedAt(Instant.now());
        when(productRepository.findById(5L)).thenReturn(Optional.of(deleted));
        assertThrows(ResourceNotFoundException.class, () -> catalogService.getProduct(5L));

        Product disabled = product(6L, category(1L, "Phones", "phones", null));
        disabled.setEnabled(false);
        when(productRepository.findById(6L)).thenReturn(Optional.of(disabled));
        assertThrows(ResourceNotFoundException.class, () -> catalogService.getProduct(6L));

        Product visible = product(7L, category(1L, "Phones", "phones", null));
        when(productRepository.findById(7L)).thenReturn(Optional.of(visible));
        assertEquals("Phone", catalogService.getProduct(7L).title());
    }

    // handles deleteProduct.
    @Test
    void deleteProductSoftDeletesInsteadOfRemovingRow() {
        Product product = product(1L, category(1L, "Phones", "phones", null));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        catalogService.deleteProduct(1L);

        assertNotNull(product.getDeletedAt());
        assertTrue(product.isDeleted());
        assertFalse(product.getEnabled());
        verify(productRepository).save(product);
        verify(productRepository, never()).delete(any(Product.class));
    }

    // === Variants ===========================================================

    // handles addVariant.
    @Test
    void addVariantSavesVariantAndInitializesStock() {
        Product product = product(1L, category(1L, "Phones", "phones", null));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.existsBySku("SKU-1")).thenReturn(false);
        when(variantRepository.save(any(ProductVariant.class))).thenAnswer(invocation -> {
            ProductVariant variant = invocation.getArgument(0);
            variant.setId(77L);
            return variant;
        });

        VariantRequest request = new VariantRequest("SKU-1", "M", "  Black  ", null, null, 5, null);

        ProductVariant saved = catalogService.addVariant(1L, request);

        assertEquals(77L, saved.getId());
        assertEquals("SKU-1", saved.getSku());
        assertEquals("M", saved.getSize());
        assertEquals("Black", saved.getColor());
        // falls back to the product base price when the request has none
        assertEquals(new BigDecimal("10.00"), saved.getPrice());
        assertEquals(true, saved.getEnabled());
        assertSame(product, saved.getProduct());
        verify(inventoryService).setStock(77L, 5, "admin:variant-created");
    }

    // handles addVariant.
    @Test
    void addVariantRejectsDuplicateSku() {
        Product product = product(1L, category(1L, "Phones", "phones", null));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(variantRepository.existsBySku("SKU-1")).thenReturn(true);

        VariantRequest request = new VariantRequest("SKU-1", "M", null, null, null, 5, null);

        assertThrows(ConflictException.class, () -> catalogService.addVariant(1L, request));
        verify(variantRepository, never()).save(any(ProductVariant.class));
        verify(inventoryService, never()).setStock(anyLong(), anyInt(), anyString());
    }

    // handles deleteVariant.
    @Test
    void deleteVariantDisablesInsteadOfRemovingRow() {
        ProductVariant variant = ProductVariant.builder()
                .id(3L)
                .sku("SKU-1")
                .size("M")
                .price(new BigDecimal("10.00"))
                .enabled(true)
                .build();
        when(variantRepository.findById(3L)).thenReturn(Optional.of(variant));

        catalogService.deleteVariant(3L);

        assertFalse(variant.getEnabled());
        verify(variantRepository).save(variant);
        verify(variantRepository, never()).delete(any(ProductVariant.class));
    }

    // === Helpers ============================================================

    private Category category(Long id, String name, String slug, Category parent) {
        Category category = new Category(name, slug, null, parent);
        category.setId(id);
        return category;
    }

    private Product product(Long id, Category category) {
        Product product = new Product("Phone", "phone", "desc", new BigDecimal("10.00"), category);
        product.setId(id);
        return product;
    }
}
