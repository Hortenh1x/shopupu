package com.example.shopupu.shipping.entity;

import com.example.shopupu.orders.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;


@Entity
@Table(name = "shipments",
        uniqueConstraints = @UniqueConstraint(name = "uq_shipments_order_id", columnNames = "order_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * describes the Shipment class.
 */
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "address_id")
    private ShippingAddress address;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShippingMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ShippingStatus status;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal cost;

    @Column(length = 8)
    private String currency;

    @Column(length = 64)
    private String trackingNumber;

    @CreationTimestamp
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;
}