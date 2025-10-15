package com.example.shopupu.orders.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

/**
 * RU: Позиция в заказе (фиксированные данные на момент покупки)
 * EN: Order line item (snapshot of data at checkout time)
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

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "line_total", nullable = false)
    private BigDecimal lineTotal;
}
