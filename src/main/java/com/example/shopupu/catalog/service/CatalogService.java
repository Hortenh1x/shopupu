package com.example.shopupu.catalog.service;

import com.example.shopupu.catalog.dto.ProductRequest;
import com.example.shopupu.catalog.dto.VariantRequest;
import com.example.shopupu.catalog.entity.Brand;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Gender;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductImage;
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
import com.example.shopupu.common.util.SlugUtil;
import com.example.shopupu.inventory.service.InventoryService;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@RequiredArgsConstructor
public class CatalogService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final ProductVariantRepository variantRepository;
    private final BrandRepository brandRepository;
    private final InventoryService inventoryService;
    private final FileStorageService fileStorageService;

    // === Categories =========================================================

    @org.springframework.cache.annotation.Cacheable(cacheNames = "categories")
    @Transactional(readOnly = true)
    public List<Category> getAllCategories() {
        return categoryRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Category getCategoryBySlug(String slug) {
        return categoryRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Category with slug " + slug + " not found"));
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "categories", allEntries = true)
    public Category createCategory(String name, String slug, String description, Long parentId) {
        if (categoryRepository.existsBySlug(slug)) {
            throw new ConflictException("Category with slug " + slug + " already exists");
        }

        Category parent = findParent(parentId);
        Category category = new Category(name, slug, description, parent);
        return categoryRepository.save(category);
    }

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "categories", allEntries = true)
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

    @org.springframework.cache.annotation.CacheEvict(cacheNames = "categories", allEntries = true)
    public void deleteCategory(Long id) {
        categoryRepository.delete(findCategory(id));
    }

    // === Brands =============================================================

    @Transactional(readOnly = true)
    public List<Brand> getAllBrands() {
        return brandRepository.findAll();
    }

    private Brand resolveBrand(String brandName) {
        if (brandName == null || brandName.isBlank()) {
            return null;
        }
        return brandRepository.findByNameIgnoreCase(brandName.trim())
                .orElseGet(() -> brandRepository.save(
                        new Brand(brandName.trim(), uniqueBrandSlug(brandName))));
    }

    private String uniqueBrandSlug(String brandName) {
        String base = SlugUtil.slugify(brandName);
        String candidate = base;
        int i = 2;
        while (brandRepository.existsBySlug(candidate)) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    // === Products ===========================================================

    public Product createProduct(ProductRequest request) {
        Category category = findCategory(request.categoryId());

        Product product = new Product();
        applyProductFields(product, request, category);
        product.setSlug(resolveProductSlug(request.slug(), request.title(), null));
        return productRepository.save(product);
    }

    public Product updateProduct(Long id, ProductRequest request) {
        Product product = findProductForAdmin(id);
        Category category = findCategory(request.categoryId());
        applyProductFields(product, request, category);
        if (request.slug() != null && !request.slug().isBlank()) {
            product.setSlug(resolveProductSlug(request.slug(), request.title(), id));
        }
        return productRepository.save(product);
    }

    private void applyProductFields(Product product, ProductRequest request, Category category) {
        product.setCategory(category);
        product.setTitle(request.title());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setOldPrice(request.oldPrice());
        product.setBrand(resolveBrand(request.brandName()));
        product.setGender(request.gender() == null ? Gender.UNISEX : request.gender());
        product.setSeason(request.season());
        product.setMaterial(request.material());
        product.setCareInstructions(request.careInstructions());
        product.setMetaTitle(request.metaTitle());
        product.setMetaDescription(request.metaDescription());
        if (request.enabled() != null) {
            product.setEnabled(request.enabled());
        }
    }

    private String resolveProductSlug(String requestedSlug, String title, Long currentProductId) {
        String base = requestedSlug != null && !requestedSlug.isBlank()
                ? requestedSlug
                : SlugUtil.slugify(title);
        if (base.isBlank()) {
            throw new BusinessRuleException("Cannot derive slug from title");
        }
        String candidate = base;
        int i = 2;
        while (true) {
            var existing = productRepository.findBySlug(candidate);
            if (existing.isEmpty() || existing.get().getId().equals(currentProductId)) {
                return candidate;
            }
            candidate = base + "-" + i++;
        }
    }

    @Transactional(readOnly = true)
    public Page<Product> getAllProducts(Pageable pageable) {
        return productRepository.findByEnabledIsTrueAndDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Product> getAllProductsForAdmin(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Product getProduct(Long id) {
        Product product = findProductForAdmin(id);
        if (!Boolean.TRUE.equals(product.getEnabled()) || product.isDeleted()) {
            throw new ResourceNotFoundException("Product with id " + id + " not found");
        }
        return product;
    }

    @Transactional(readOnly = true)
    public Product getProductForAdmin(Long id) {
        return findProductForAdmin(id);
    }

    @Transactional(readOnly = true)
    public Page<Product> getProductsByCategory(String slug, Pageable pageable) {
        return productRepository.findByCategory_SlugAndEnabledIsTrueAndDeletedAtIsNull(slug, pageable);
    }

    /** Soft delete (DB-11): keeps the row for order history, hides it everywhere. */
    public void deleteProduct(Long id) {
        Product product = findProductForAdmin(id);
        product.setEnabled(false);
        product.setDeletedAt(Instant.now());
        productRepository.save(product);
    }

    // === Variants ===========================================================

    @Transactional(readOnly = true)
    public List<ProductVariant> getVariants(Long productId) {
        findProductForAdmin(productId);
        return variantRepository.findByProduct_Id(productId);
    }

    public ProductVariant addVariant(Long productId, VariantRequest request) {
        Product product = findProductForAdmin(productId);
        if (variantRepository.existsBySku(request.sku())) {
            throw new ConflictException("Variant with sku " + request.sku() + " already exists");
        }

        ProductVariant variant = ProductVariant.builder()
                .product(product)
                .sku(request.sku())
                .size(request.size())
                .color(normalizeColor(request.color()))
                .price(request.price() != null ? request.price() : product.getPrice())
                .oldPrice(request.oldPrice())
                .enabled(request.enabled() == null || request.enabled())
                .build();
        ProductVariant saved = variantRepository.save(variant);
        inventoryService.setStock(saved.getId(),
                request.stock() == null ? 0 : request.stock(), "admin:variant-created");
        return saved;
    }

    public ProductVariant updateVariant(Long variantId, VariantRequest request) {
        ProductVariant variant = findVariant(variantId);
        var existing = variantRepository.findBySku(request.sku());
        if (existing.isPresent() && !existing.get().getId().equals(variantId)) {
            throw new ConflictException("Variant with sku " + request.sku() + " already exists");
        }

        variant.setSku(request.sku());
        variant.setSize(request.size());
        variant.setColor(normalizeColor(request.color()));
        if (request.price() != null) {
            variant.setPrice(request.price());
        }
        variant.setOldPrice(request.oldPrice());
        if (request.enabled() != null) {
            variant.setEnabled(request.enabled());
        }
        ProductVariant saved = variantRepository.save(variant);
        if (request.stock() != null) {
            inventoryService.setStock(variantId, request.stock(), "admin:variant-updated");
        }
        return saved;
    }

    /** Variants referenced by orders/carts must not disappear; disable instead. */
    public void deleteVariant(Long variantId) {
        ProductVariant variant = findVariant(variantId);
        variant.setEnabled(false);
        variantRepository.save(variant);
    }

    // === Images =============================================================

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

    // === Helpers ============================================================

    private String normalizeColor(String color) {
        return color == null || color.isBlank() ? null : color.trim();
    }

    private ProductVariant findVariant(Long variantId) {
        return variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant with id " + variantId + " not found"));
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
