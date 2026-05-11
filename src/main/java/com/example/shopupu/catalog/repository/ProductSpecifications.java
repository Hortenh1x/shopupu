package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.Product;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {
    // handles ProductSpecifications.
    private ProductSpecifications() {}

    // handles textSearch.
    public static Specification<Product> textSearch(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";

        return (root, cq, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("description")), like),
                cb.like(cb.lower(root.get("sku")), like)
                );
    }

    // handles byCategoryId.
    public static Specification<Product> byCategoryId(Long categoryId) {
        if (categoryId == null) return null;

        return (root, cq, cb) -> cb.equal(
                root.join("category", JoinType.LEFT).get("id"), categoryId
        );
    }

    // handles minPrice.
    public static Specification<Product> minPrice(Double min) {
        if (min == null) return null;
        return (root, cq, cb) -> cb.ge(root.get("price"), min);
    }

    // handles maxPrice.
    public static Specification<Product> maxPrice(Double max) {
        if (max == null) return null;
        return (root, cq, cb) -> cb.le(root.get("price"), max);
    }

    // handles byEnabled.
    public static Specification<Product> byEnabled(Boolean enabled) {
        if (enabled == null) return null;
        return(root, cq, cb) -> cb.equal(root.get("enabled"), enabled);
    }



    // handles build.
    public static Specification<Product> build(
            String q, Long categoryId, Double min, Double max, Boolean enabled
    ) {
        Specification<Product> spec = (root, query, cb) -> cb.conjunction();

        if (textSearch(q) != null)              spec = spec.and(textSearch(q));
        if (byCategoryId(categoryId) != null)   spec = spec.and(byCategoryId(categoryId));
        if (minPrice(min) != null)              spec = spec.and(minPrice(min));
        if (maxPrice(max) != null)              spec = spec.and(maxPrice(max));
        if (byEnabled(enabled) != null)         spec = spec.and(byEnabled(enabled));

        return spec;
    }
}