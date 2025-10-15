package com.example.shopupu.payments.entity;

import com.example.shopupu.orders.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payments",
        indexes = {
                @Index(name = "idx_payments_provider_payment_id", columnList = "provider_payment_id", unique = true),
                @Index(name = "idx_payments_idempotency_key", columnList = "idempotency_key", unique = true)
        })

@Getter @Setter @Builder
@NoArgsConstructor @AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional=false, fetch = FetchType.LAZY)
    @JoinColumn(name="order_id")
    private Order order;

    @Column(nullable=false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable=false, length=3)
    private String currency; // RU: ISO-4217, пример "USD" / EN: ISO-4217 like "USD"

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=32)
    private PaymentStatus status;

    @Column(nullable=false, length=64)
    private String provider;

    @Column(name="provider_pid", length=128, unique=false)
    private String providerPaymentId;

    @Column(name="client_secret", length=128)
    private String clientSecret;

    @Column(name="created_at", nullable=false, updatable=false)
    private Instant createdAt = Instant.now();

    @UpdateTimestamp
    @Column(name="updated_at", nullable=false)
    private Instant updatedAt;

    // RU: ключ идемпотентности наших вызовов (unique)
    // EN: our idempotency key (unique)
    @Column(name = "idempotency_key", length = 128, nullable = false)
    private String idempotencyKey;

    // RU: для контроля количества попыток (ретраи)
    // EN: attempt counter (retries)
    @Column(name = "attempts", nullable = false)
    private int attempts;
}