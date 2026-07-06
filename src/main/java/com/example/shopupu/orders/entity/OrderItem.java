package com.example.shopupu.orders.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.*;


/**
 * Immutable snapshot of the purchased variant (ORD-02): title, SKU, size, color,
 * brand and price are copied at checkout so later catalog edits never change history.
 */
@Entity
@Table(name = "order_items")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "variant_id")
    private Long variantId;

    @Column(nullable = false)
    private String title;

    @Column(length = 64)
    private String sku;

    @Column(length = 32)
    private String size;

    @Column(length = 64)
    private String color;

    @Column(length = 255)
    private String brand;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_total", nullable = false, precision = 19, scale = 2)
    private BigDecimal lineTotal;
}
