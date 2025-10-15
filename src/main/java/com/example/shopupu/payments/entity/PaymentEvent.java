package com.example.shopupu.payments.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "payment_events",
        indexes = @Index(name = "idx_payment_events_event_id", columnList = "event_id", unique = true))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PaymentEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 128)
    private String eventId; // Stripe event id

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;
}
