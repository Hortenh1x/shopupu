package com.example.shopupu.promo.repository;

import com.example.shopupu.promo.entity.PromoCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PromoCodeRepository extends JpaRepository<PromoCode, Long> {

    Optional<PromoCode> findByCodeIgnoreCase(String code);

    /**
     * Atomic redemption counter (PROMO-03): succeeds only while the global
     * limit is not exhausted, so concurrent checkouts cannot overuse a code.
     */
    @Modifying
    @Query("""
            update PromoCode p set p.redemptionCount = p.redemptionCount + 1, p.version = p.version + 1
            where p.id = :id and (p.maxRedemptions is null or p.redemptionCount < p.maxRedemptions)""")
    int tryRedeem(@Param("id") Long id);
}
