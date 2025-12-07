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

/**
 * RU: Сущность платежа (содержит данные о транзакции у провайдера).
 * EN: Payment entity (stores transaction details from provider).
 */
@Entity
@Table(name = "payments")
@Getter @Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** RU: Связанный заказ / EN: Related order */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** RU: Провайдер (stripe / paypal) / EN: Provider name */
    @Column(nullable = false, length = 32)
    private String provider;

    /** RU: Внешний ID платежа у провайдера / EN: External payment ID (from Stripe) */
    @Column(name = "external_id", unique = true)
    private String externalId;

    /** RU: Секрет клиента (Stripe client secret) / EN: Client secret returned by Stripe */
    @Column(name = "client_secret", length = 255)
    private String clientSecret;

    /** RU: Сумма платежа / EN: Payment amount */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** RU: Валюта / EN: Currency */
    @Column(nullable = false, length = 8)
    private String currency;

    /** RU: Статус платежа / EN: Payment status */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus status;

    /** RU: Ключ идемпотентности для защиты от повторных запросов.
     *  EN: Idempotency key to guard against duplicate requests. */
    @Column(name = "idempotency_key", nullable = false, length = 64, unique = true)
    private String idempotencyKey;

    /** RU: Создано / EN: Created timestamp */
    @CreationTimestamp
    private Instant createdAt;

    /** RU: Обновлено / EN: Updated timestamp */
    @UpdateTimestamp
    private Instant updatedAt;

    @PrePersist
    private void ensureIdempotencyKey() {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            idempotencyKey = UUID.randomUUID().toString();
        }
    }
}
