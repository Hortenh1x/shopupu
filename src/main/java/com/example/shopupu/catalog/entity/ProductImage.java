package com.example.shopupu.catalog.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "product_images")
public class ProductImage {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "text")
    private String url;

    @Column(name = "alt_text", length = 255)
    private String altText;

    @Column(nullable = false)
    private Integer position = 0;

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

    public Long getId() { return id; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getAltText() { return altText; }
    public void setAltText(String altText) { this.altText = altText; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
}