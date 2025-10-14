package com.example.shopupu.catalog.repository;

import com.example.shopupu.catalog.entity.Product;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {
    private ProductSpecifications() {}

    public static Specification<Product> textSearch(String q) {
        if (q == null || q.isBlank()) return null;
        String like = "%" + q.trim().toLowerCase() + "%";
        // WHERE lower(title) like ? OR lower(description) like ? OR lower(sku) like ?
        return (root, cq, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), like),
                cb.like(cb.lower(root.get("description")), like),
                cb.like(cb.lower(root.get("sku")), like)
                );
    }

    public static Specification<Product> byCategoryId(Long categoryId) {
        if (categoryId == null) return null;
        //JOIN category и фильтр по ее id
        return (root, cq, cb) -> cb.equal(
                root.join("category", JoinType.LEFT).get("id"), categoryId
        );
    }

    public static Specification<Product> minPrice(Double min) {
        if (min == null) return null;
        return (root, cq, cb) -> cb.ge(root.get("price"), min);
    }

    public static Specification<Product> maxPrice(Double max) {
        if (max == null) return null;
        return (root, cq, cb) -> cb.le(root.get("price"), max);
    }

    public static Specification<Product> byEnabled(Boolean enabled) {
        if (enabled == null) return null;
        return(root, cq, cb) -> cb.equal(root.get("enabled"), enabled);
    }

    /** Склейка всего вместе: игнорируем null-ы (они просто не добавятся в WHERE). */

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