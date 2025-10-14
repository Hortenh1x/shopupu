package com.example.shopupu.cart.entity;

import com.example.shopupu.catalog.entity.Product;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * RU: Позиция корзины: товар + количество (уникальна по (cart, product))
 * EN: Cart line: product + quantity (unique by (cart, product))
 */
@Entity
@Table(name = "cart_items",
        uniqueConstraints = @UniqueConstraint(name="uq_cart_product", columnNames = {"cart_id","product_id"}))
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CartItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // RU: Владелец — корзина
    // EN: Owner — cart
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cart_id", nullable = false)
    private Cart cart;

    // RU: Ссылка на товар
    // EN: Linked product
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    // RU: Количество товара
    // EN: Quantity
    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}