package com.example.shopupu.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Setter
    @Column(name = "alt_text", length = 255)
    private String altText;

    @Setter
    @Column(nullable = false)
    private Integer position = 0;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    public ProductImage() {}

    public ProductImage(String url, String altText, Integer position, Product product) {
        this.url = url;
        this.altText = altText;
        this.position = position;
        this.product = product;
    }

}