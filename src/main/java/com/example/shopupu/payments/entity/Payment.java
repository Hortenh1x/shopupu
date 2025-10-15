package com.example.shopupu.payments.entity;

import com.example.shopupu.orders.entity.Order;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * RU: Платёж, связанный с заказом. Содержит данные о транзакции в платёжной системе.
 * EN: Payment linked to an order. Stores transaction details from external provider.
 */
@Entity
@Table(name = "payments",
        indexes = {
                @Index(name = "idx_payment_external_id", columnList = "external_id"),
                @Index(name = "idx_payment_provider", columnList = "provider")
        },
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_payment_idempotency_key", columnNames = "idempotency_key")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * RU: Заказ, к которому относится платёж.
     * EN: The order this payment belongs to.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * RU: Уникальный идентификатор транзакции у провайдера (например, Stripe или PayPal).
     * EN: External transaction ID (from Stripe, PayPal, etc.).
     */
    @Column(name = "external_id", length = 128)
    private String externalId;

    /**
     * RU: Имя платёжного провайдера (STRIPE, PAYPAL, MOCK и т.д.).
     * EN: Payment provider name (STRIPE, PAYPAL, MOCK, etc.).
     */
    @Column(nullable = false, length = 50)
    private String provider;

    /**
     * RU: Сумма платежа (в валюте магазина).
     * EN: Payment amount (in store currency).
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    /**
     * RU: Валюта платежа (например, EUR, USD).
     * EN: Payment currency (e.g. EUR, USD).
     */
    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * RU: Текущий статус платежа.
     * Возможные значения: CREATED, PENDING, SUCCESS, FAILED, REFUNDED.
     * EN: Current payment status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    /**
     * RU: Уникальный ключ для идемпотентности.
     * EN: Unique key to prevent duplicate processing.
     */
    @Column(name = "idempotency_key", nullable = false, unique = true, length = 64)
    private String idempotencyKey;

    /**
     * RU: Дата/время создания платежа.
     * EN: Payment creation timestamp.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * RU: Дата/время последнего обновления.
     * EN: Last update timestamp.
     */
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * RU: Дополнительные данные от провайдера (например, JSON-ответ Stripe).
     * EN: Optional raw provider payload (for debugging or refunds).
     */
    @Lob
    @Column(name = "provider_payload", columnDefinition = "TEXT")
    private String providerPayload;
}
