package com.example.shopupu.payments.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * RU: Событие, фиксирующее изменение статуса платежа.
 * EN: Event recording a payment status change.
 */
@Entity
@Table(name = "payment_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * RU: Ссылка на сам платёж.
     * EN: Reference to payment.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    /**
     * RU: Новый статус (например, SUCCESS, FAILED, REFUNDED).
     * EN: New status (e.g. SUCCESS, FAILED, REFUNDED).
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus newStatus;

    /**
     * RU: Происхождение события (например, STRIPE_WEBHOOK, SYSTEM, MANUAL).
     * EN: Event source (e.g. STRIPE_WEBHOOK, SYSTEM, MANUAL).
     */
    @Column(nullable = false, length = 50)
    private String source;

    /**
     * RU: Дополнительные данные (например, сообщение об ошибке или payload).
     * EN: Optional message or JSON payload.
     */
    @Column(columnDefinition = "TEXT")
    private String details;

    /**
     * RU: Время события.
     * EN: Timestamp of event.
     */
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
