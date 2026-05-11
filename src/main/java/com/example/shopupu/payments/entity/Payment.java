package com.example.shopupu.payments.entity;

import com.example.shopupu.orders.entity.Order;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Entity
@Table(name = "payments")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * describes the Payment class.
 */
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;


    @Column(nullable = false, length = 32)
    private String provider;


    @Column(name = "external_id", length = 128, unique = true)
    private String externalId;


    @Column(name = "client_secret", length = 255)
    private String clientSecret;

    @Column(name = "payment_url", length = 512)
    private String paymentUrl;

    @Column(name = "client_token", length = 255)
    private String clientToken;


    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;


    @Column(nullable = false, length = 8)
    private String currency;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;


    @Column(name = "idempotency_key", nullable = false, length = 64, unique = true)
    private String idempotencyKey;


    @CreationTimestamp
    private Instant createdAt;


    @UpdateTimestamp
    private Instant updatedAt;

    @PrePersist
    // handles ensureIdempotencyKey.
    private void ensureIdempotencyKey() {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }
    }
}