package com.example.shopupu.catalog.service;

import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional // D��?D� D���D�D�D,��D��<D� D�D�,D_D'�< �?" D� �,�?D�D�D�D�D��+D,D,
public class CatalogService {
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CatalogService(CategoryRepository categoryRepository, ProductRepository productRepository) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }


    public Category createCategory(String name, String slug, String description, Long parentId) {
        if (categoryRepository.existsBySlug(slug)) {
            throw new IllegalArgumentException("Category with slug " + slug + " already exists");
        }
        Category parent = null;
        if(parentId != null) {
            parent = categoryRepository.findById(parentId).orElseThrow(() -> new IllegalArgumentException("Parent category with id " + parentId + " not found"));
        }
        Category category = new Category(name, slug, description, parent);
        return categoryRepository.save(category);
    }


    public Product createProduct(Long categoryId, String title, String description, BigDecimal price, String sku, Integer stock, Boolean enabled) {
        var category = categoryRepository.findById(categoryId).orElseThrow(() -> new IllegalArgumentException("Category with id " + categoryId + " not found"));

        if (productRepository.findBySku(sku).isPresent()) {
            throw new IllegalArgumentException("Product with sku " + sku + " already exists");
        }

        var product = new Product(title, description, price, sku, stock, category);
        if (enabled != null) {
            product.setEnabled(enabled);
        }
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getProductsByCategory(String slug) {
        return productRepository.findByCategory_Slug(slug);
    }
}

