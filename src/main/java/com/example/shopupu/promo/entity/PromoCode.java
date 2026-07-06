package com.example.shopupu.promo.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "promo_codes")
public class PromoCode {

    public enum Type {
        PERCENT,
        FIXED,
        FREE_SHIPPING
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "promo_type", nullable = false, length = 16)
    private Type promoType;

    /** Percent (0-100) for PERCENT, absolute amount for FIXED, unused for FREE_SHIPPING. */
    @Builder.Default
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal value = BigDecimal.ZERO;

    @Column(name = "min_order_amount", precision = 19, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "starts_at")
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    /** Global usage cap; null = unlimited. */
    @Column(name = "max_redemptions")
    private Integer maxRedemptions;

    @Builder.Default
    @Column(name = "per_user_limit", nullable = false)
    private int perUserLimit = 1;

    @Builder.Default
    @Column(name = "redemption_count", nullable = false)
    private int redemptionCount = 0;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Version
    @Column(nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
