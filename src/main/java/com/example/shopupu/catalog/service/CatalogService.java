package com.example.shopupu.catalog.service;

import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductImage;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.repository.ProductImageRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.storage.FileStorageService;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ConflictException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final FileStorageService fileStorageService;

    public Category createCategory(String name, String slug, String description, Long parentId) {
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Category with slug " + slug + " already exists");
        }

        Category parent = findParent(parentId);
        Category category = new Category(name, slug, description, parent);
        return categoryRepository.save(category);
    }

    public Category updateCategory(Long id, String name, String slug, String description, Long parentId) {
        Category category = findCategory(id);
        ensureSlugIsFree(slug, id);

        Category parent = findParent(parentId);
        validateParent(category, parent);

        category.setName(name);
        category.setSlug(slug);
        category.setDescription(description);
        category.setParent(parent);
        return categoryRepository.save(category);
    }

    public Product createProduct(Long categoryId, String title, String description, BigDecimal price, String sku, Integer stock, Boolean enabled) {
        Category category = findCategory(categoryId);

        if (productRepository.findBySku(sku).isPresent()) {
            throw new ConflictException("Product with sku " + sku + " already exists");
        }

        var product = new Product(title, description, price, sku, stock, category);
        if (enabled != null) {
            product.setEnabled(enabled);
        }
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, Long categoryId, String title, String description, BigDecimal price, String sku, Integer stock, Boolean enabled) {
        Product product = findProductForAdmin(id);
        Category category = findCategory(categoryId);
        ensureSkuIsFree(sku, id);

        product.setCategory(category);
        product.setTitle(title);
        product.setDescription(description);
        product.setPrice(price);
        product.setSku(sku);
        product.setStock(stock);
        if (enabled != null) {
            product.setEnabled(enabled);
        }
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findByEnabledIsTrue();
    }

    public List<Product> getAllProductsForAdmin() {
        return productRepository.findAll();
    }

    public Product getProduct(Long id) {
        Product product = findProductForAdmin(id);
        if (!Boolean.TRUE.equals(product.getEnabled())) {
            throw new ResourceNotFoundException("Product with id " + id + " not found");
        }
        return product;
    }

    public Product getProductForAdmin(Long id) {
        return findProductForAdmin(id);
    }

    public List<Product> getProductsByCategory(String slug) {
        return productRepository.findByCategory_SlugAndEnabledIsTrue(slug);
    }

    public void deleteCategory(Long id) {
        categoryRepository.delete(findCategory(id));
    }

    public void deleteProduct(Long id) {
        productRepository.delete(findProductForAdmin(id));
    }

    public ProductImage addProductImage(Long productId, org.springframework.web.multipart.MultipartFile file, String altText, Integer sortOrder) {
        Product product = findProductForAdmin(productId);
        String url = fileStorageService.storeProductImage(file);
        ProductImage image = new ProductImage(url, altText, sortOrder == null ? 0 : sortOrder, product);
        product.getImages().add(image);
        return productImageRepository.save(image);
    }

    public void deleteProductImage(Long productId, Long imageId) {
        ProductImage image = productImageRepository.findByIdAndProductId(imageId, productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product image with id " + imageId + " not found"));
        productImageRepository.delete(image);
    }

    private Category findCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category with id " + id + " not found"));
    }

    private Product findProductForAdmin(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + id + " not found"));
    }

    private Category findParent(Long parentId) {
        if (parentId == null) {
            return null;
        }
        return categoryRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("Parent category with id " + parentId + " not found"));
    }

    private void ensureSlugIsFree(String slug, Long currentCategoryId) {
        Category existing = categoryRepository.findBySlug(slug).orElse(null);
        if (existing != null && !existing.getId().equals(currentCategoryId)) {
            throw new ConflictException("Category with slug " + slug + " already exists");
        }
    }

    private void ensureSkuIsFree(String sku, Long currentProductId) {
        Product existing = productRepository.findBySku(sku).orElse(null);
        if (existing != null && !existing.getId().equals(currentProductId)) {
            throw new ConflictException("Product with sku " + sku + " already exists");
        }
    }

    private void validateParent(Category category, Category parent) {
        if (parent == null) {
            return;
        }
        if (parent.getId().equals(category.getId())) {
            throw new BusinessRuleException("Category cannot be its own parent");
        }
        if (isDescendant(parent, category)) {
            throw new BusinessRuleException("Category parent would create a cycle");
        }
    }

    private boolean isDescendant(Category possibleChild, Category parent) {
        Category current = possibleChild;
        while (current != null) {
            if (current.getId() != null && current.getId().equals(parent.getId())) {
                return true;
            }
            current = current.getParent();
        }
        return false;
    }
}
