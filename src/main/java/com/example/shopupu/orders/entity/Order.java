package com.example.shopupu.orders.entity;

import com.example.shopupu.identity.entity.User;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;


@Entity
@Table(name = "orders")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Human-readable business identifier shown to customers (ORD-08). */
    @Column(name = "order_number", nullable = false, unique = true, length = 32)
    private String orderNumber;

    /** Client-supplied Idempotency-Key; unique per user (ORD-03/API-04). */
    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder.Default
    @Column(name = "subtotal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal subtotalAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "shipping_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal shippingAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "discount_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Code applied at checkout; kept for history and FREE_SHIPPING recalculation. */
    @Column(name = "promo_code", length = 64)
    private String promoCode;

    @Builder.Default
    @Column(name = "payment_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paymentAmount = BigDecimal.ZERO;

    @Column(nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    @Builder.Default
    private OrderStatus status = OrderStatus.CREATED;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @org.hibernate.annotations.BatchSize(size = 50)
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();
}
