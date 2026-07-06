package com.example.shopupu.inventory.entity;

import com.example.shopupu.catalog.entity.ProductVariant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/** Stock per SKU. {@code reserved} is held by unpaid orders; available = stock - reserved. */
@Getter
@Setter
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "inventory")
public class Inventory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "variant_id", nullable = false, unique = true)
    private ProductVariant variant;

    @Builder.Default
    @Column(nullable = false)
    private int stock = 0;

    @Builder.Default
    @Column(nullable = false)
    private int reserved = 0;

    @Version
    @Column(nullable = false)
    private long version;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public int getAvailable() {
        return stock - reserved;
    }
}
