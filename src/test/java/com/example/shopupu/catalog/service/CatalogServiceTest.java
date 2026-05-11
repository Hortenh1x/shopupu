package com.example.shopupu.catalog.service;

import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ConflictException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * describes the CatalogServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class CatalogServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private CatalogService catalogService;

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

    // handles createProduct.
    @Test
    void createProductSavesProductWithCategory() {
        Category category = category(1L, "Phones", "phones", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.findBySku("sku-1")).thenReturn(Optional.empty());
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product product = catalogService.createProduct(1L, "Phone", "desc", new BigDecimal("10.00"), "sku-1", 5, false);

        assertEquals("Phone", product.getTitle());
        assertEquals(false, product.getEnabled());
        assertSame(category, product.getCategory());
    }

    // handles createProduct.
    @Test
    void createProductRejectsMissingCategoryAndDuplicateSku() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> catalogService.createProduct(1L, "Phone", null, BigDecimal.ONE, "sku", 1, true));

        Category category = category(1L, "Phones", "phones", null);
        Product existing = product(2L, category);
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(category));
        when(productRepository.findBySku("sku")).thenReturn(Optional.of(existing));
        assertThrows(ConflictException.class, () -> catalogService.createProduct(2L, "Phone", null, BigDecimal.ONE, "sku", 1, true));
    }

    // handles updateProduct.
    @Test
    void updateProductChangesFields() {
        Category oldCategory = category(1L, "Old", "old", null);
        Category newCategory = category(2L, "New", "new", null);
        Product product = product(10L, oldCategory);
        when(productRepository.findById(10L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(2L)).thenReturn(Optional.of(newCategory));
        when(productRepository.findBySku("new-sku")).thenReturn(Optional.empty());
        when(productRepository.save(product)).thenReturn(product);

        Product updated = catalogService.updateProduct(10L, 2L, "Updated", "desc", new BigDecimal("20.00"), "new-sku", 8, false);

        assertEquals("Updated", updated.getTitle());
        assertEquals("new-sku", updated.getSku());
        assertEquals(8, updated.getStock());
        assertSame(newCategory, updated.getCategory());
    }

    // handles getAllProducts.
    @Test
    void getAllProductsReturnsOnlyEnabledProducts() {
        Product product = product(1L, category(1L, "Phones", "phones", null));
        when(productRepository.findByEnabledIsTrue()).thenReturn(List.of(product));

        assertEquals(List.of(product), catalogService.getAllProducts());
    }

    // handles getProductsByCategory.
    @Test
    void getProductsByCategoryReturnsOnlyEnabledCategoryProducts() {
        Product product = product(1L, category(1L, "Phones", "phones", null));
        when(productRepository.findByCategory_SlugAndEnabledIsTrue("phones")).thenReturn(List.of(product));

        assertEquals(List.of(product), catalogService.getProductsByCategory("phones"));
    }

    // handles deleteCategory.
    @Test
    void deleteCategoryDeletesExistingCategory() {
        Category category = category(1L, "Phones", "phones", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        catalogService.deleteCategory(1L);

        verify(categoryRepository).delete(category);
    }

    // handles deleteProduct.
    @Test
    void deleteProductDeletesExistingProduct() {
        Product product = product(1L, category(1L, "Phones", "phones", null));
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        catalogService.deleteProduct(1L);

        verify(productRepository).delete(product);
    }

    private Category category(Long id, String name, String slug, Category parent) {
        Category category = new Category(name, slug, null, parent);
        category.setId(id);
        return category;
    }

    private Product product(Long id, Category category) {
        Product product = new Product("Phone", "desc", new BigDecimal("10.00"), "sku", 5, category);
        product.setId(id);
        return product;
    }
}
