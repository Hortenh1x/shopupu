package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.catalog.model.ProductFilter;
import com.example.shopupu.inventory.entity.Inventory;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {}

    public static Specification<Product> textSearch(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";

        return (root, cq, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("description")), like)
        );
    }

    public static Specification<Product> byCategoryId(Long categoryId) {
        if (categoryId == null) return null;

        return (root, cq, cb) -> cb.equal(
                root.join("category", JoinType.LEFT).get("id"), categoryId
        );
    }

    public static Specification<Product> byBrandId(Long brandId) {
        if (brandId == null) return null;
        return (root, cq, cb) -> cb.equal(root.get("brand").get("id"), brandId);
    }

    public static Specification<Product> byGender(com.example.shopupu.catalog.entity.Gender gender) {
        if (gender == null) return null;
        return (root, cq, cb) -> cb.equal(root.get("gender"), gender);
    }

    public static Specification<Product> byEnabled(Boolean enabled) {
        if (enabled == null) return null;
        return (root, cq, cb) -> cb.equal(root.get("enabled"), enabled);
    }

    public static Specification<Product> notDeleted() {
        return (root, cq, cb) -> cb.isNull(root.get("deletedAt"));
    }

    /**
     * EXISTS subquery over variants: size, color, price range and stock
     * availability are variant-level attributes (CAT-01/CAT-05).
     */
    public static Specification<Product> hasMatchingVariant(
            String size, String color, BigDecimal minPrice, BigDecimal maxPrice, Boolean inStock) {
        boolean noVariantFilters = size == null && color == null
                && minPrice == null && maxPrice == null && !Boolean.TRUE.equals(inStock);
        if (noVariantFilters) {
            return null;
        }

        return (root, cq, cb) -> {
            Subquery<Long> sub = cq.subquery(Long.class);
            Root<ProductVariant> variant = sub.from(ProductVariant.class);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(variant.get("product"), root));
            predicates.add(cb.isTrue(variant.get("enabled")));
            if (size != null && !size.isBlank()) {
                predicates.add(cb.equal(cb.lower(variant.get("size")), size.trim().toLowerCase()));
            }
            if (color != null && !color.isBlank()) {
                predicates.add(cb.equal(cb.lower(variant.get("color")), color.trim().toLowerCase()));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(variant.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(variant.get("price"), maxPrice));
            }
            if (Boolean.TRUE.equals(inStock)) {
                Subquery<Long> stockSub = cq.subquery(Long.class);
                Root<Inventory> inv = stockSub.from(Inventory.class);
                stockSub.select(inv.get("id"));
                stockSub.where(
                        cb.equal(inv.get("variant"), variant),
                        cb.greaterThan(cb.diff(inv.get("stock"), inv.get("reserved")), 0)
                );
                predicates.add(cb.exists(stockSub));
            }
            sub.select(variant.get("id"));
            sub.where(predicates.toArray(Predicate[]::new));
            return cb.exists(sub);
        };
    }

    public static Specification<Product> build(ProductFilter f) {
        Specification<Product> spec = notDeleted();

        Specification<Product> text = textSearch(f.q);
        if (text != null) spec = spec.and(text);
        Specification<Product> cat = byCategoryId(f.categoryId);
        if (cat != null) spec = spec.and(cat);
        Specification<Product> brand = byBrandId(f.brandId);
        if (brand != null) spec = spec.and(brand);
        Specification<Product> gender = byGender(f.gender);
        if (gender != null) spec = spec.and(gender);
        Specification<Product> enabled = byEnabled(f.enabled);
        if (enabled != null) spec = spec.and(enabled);
        Specification<Product> variant = hasMatchingVariant(f.size, f.color, f.minPrice, f.maxPrice, f.inStock);
        if (variant != null) spec = spec.and(variant);

        return spec;
    }
}
