package com.example.shopupu.payments.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;


@Entity
@Table(name = "payment_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * describes the PaymentEvent class.
 */
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(name = "external_event_id", length = 128, unique = true)
    private String externalEventId;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentStatus newStatus;


    @Column(nullable = false, length = 50)
    private String source;


    @Column(columnDefinition = "TEXT")
    private String details;


    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}