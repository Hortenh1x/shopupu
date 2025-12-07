package com.example.shopupu.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

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

    @Column(columnDefinition = "text")
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;               // DECIMAL → BigDecimal

    @Column(nullable = false, unique = true, length = 64)
    private String sku;                     // уникальный артикул

    @Column(nullable = false)
    private Integer stock = 0;              // остатки

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    // Много товаров -> одна категория
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    // Обратная сторона связи с картинками
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ProductImage> images = new ArrayList<>();

    public Product() {}

    public Product(String title, String description, BigDecimal price, String sku, Integer stock, Category category) {
        this.title = title;
        this.description = description;
        this.price = price;
        this.sku = sku;
        this.stock = stock;
        this.category = category;
    }


}
