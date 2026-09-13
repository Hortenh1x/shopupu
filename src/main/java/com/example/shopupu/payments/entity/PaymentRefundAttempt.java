package com.example.shopupu.payments.entity;

import com.example.shopupu.payments.gateway.PaymentGatewayRefundStatus;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Entity
@Table(name = "payment_refund_attempts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PaymentRefundAttempt {
    @Id
    @Column(name = "operation_key", length = 64)
    private String operationKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Column(nullable = false, length = 32)
    private String provider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentGatewayRefundStatus status;

    @Column(name = "external_refund_id", length = 128)
    private String externalRefundId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
