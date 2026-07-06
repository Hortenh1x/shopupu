package com.example.shopupu.catalog.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * A product model (e.g. "Oversized Cotton Hoodie"). Sellable units are its
 * {@link ProductVariant}s (size/color/SKU); stock lives in Inventory per variant.
 */
@Getter
@Setter
@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(nullable = false, unique = true, length = 255)
    private String slug;

    @Column(columnDefinition = "text")
    private String description;

    /** Base/display price; each variant carries its own effective price. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    /** Previous price for discount display (CAT-09). */
    @Column(name = "old_price", precision = 19, scale = 2)
    private BigDecimal oldPrice;

    @Column(nullable = false)
    private Boolean enabled = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "brand_id")
    private Brand brand;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Gender gender = Gender.UNISEX;

    @Column(length = 32)
    private String season;

    @Column(length = 255)
    private String material;

    @Column(name = "care_instructions", columnDefinition = "text")
    private String careInstructions;

    @Column(name = "meta_title", length = 255)
    private String metaTitle;

    @Column(name = "meta_description", length = 512)
    private String metaDescription;

    /** Soft delete (DB-11): hidden everywhere, kept for order history. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductVariant> variants = new ArrayList<>();

    public Product() {}

    public Product(String title, String slug, String description, BigDecimal price, Category category) {
        this.title = title;
        this.slug = slug;
        this.description = description;
        this.price = price;
        this.category = category;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
