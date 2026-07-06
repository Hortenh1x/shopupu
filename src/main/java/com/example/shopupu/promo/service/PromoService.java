package com.example.shopupu.promo.service;

import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.promo.entity.PromoCode;
import com.example.shopupu.promo.entity.PromoRedemption;
import com.example.shopupu.promo.repository.PromoCodeRepository;
import com.example.shopupu.promo.repository.PromoRedemptionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PromoService {

    private final PromoCodeRepository promoCodeRepository;
    private final PromoRedemptionRepository redemptionRepository;

    /**
     * Validates a code for the user and order subtotal (PROMO-02). Called both
     * on pre-check ("validate" endpoint) and again inside checkout.
     */
    @Transactional(readOnly = true)
    public PromoCode validate(String code, User user, BigDecimal subtotal) {
        PromoCode promo = promoCodeRepository.findByCodeIgnoreCase(normalize(code))
                .orElseThrow(() -> new BusinessRuleException("Promo code is not valid"));

        Instant now = Instant.now();
        if (!Boolean.TRUE.equals(promo.getEnabled())
                || (promo.getStartsAt() != null && now.isBefore(promo.getStartsAt()))
                || (promo.getEndsAt() != null && now.isAfter(promo.getEndsAt()))) {
            throw new BusinessRuleException("Promo code is not valid");
        }
        if (promo.getMaxRedemptions() != null && promo.getRedemptionCount() >= promo.getMaxRedemptions()) {
            throw new BusinessRuleException("Promo code is exhausted");
        }
        if (promo.getMinOrderAmount() != null && subtotal.compareTo(promo.getMinOrderAmount()) < 0) {
            throw new BusinessRuleException("Order total is below the promo code minimum");
        }
        if (redemptionRepository.countByPromoAndUser(promo, user) >= promo.getPerUserLimit()) {
            throw new BusinessRuleException("Promo code already used");
        }
        return promo;
    }

    /** Discount on the item subtotal; FREE_SHIPPING is applied to shipping instead. */
    public BigDecimal discountFor(PromoCode promo, BigDecimal subtotal) {
        return switch (promo.getPromoType()) {
            case PERCENT -> subtotal.multiply(promo.getValue())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            case FIXED -> promo.getValue().min(subtotal);
            case FREE_SHIPPING -> BigDecimal.ZERO;
        };
    }

    /**
     * Consumes one redemption atomically (PROMO-03); a concurrent request past
     * the global limit loses the UPDATE race and gets a clean error.
     */
    @Transactional
    public void redeem(PromoCode promo, User user, Order order) {
        if (redemptionRepository.countByPromoAndUser(promo, user) >= promo.getPerUserLimit()) {
            throw new BusinessRuleException("Promo code already used");
        }
        int updated = promoCodeRepository.tryRedeem(promo.getId());
        if (updated == 0) {
            throw new BusinessRuleException("Promo code is exhausted");
        }
        redemptionRepository.save(PromoRedemption.builder()
                .promo(promo)
                .user(user)
                .order(order)
                .build());
    }

    @Transactional(readOnly = true)
    public org.springframework.data.domain.Page<PromoCode> list(org.springframework.data.domain.Pageable pageable) {
        return promoCodeRepository.findAll(pageable);
    }

    @Transactional
    public PromoCode create(com.example.shopupu.promo.dto.PromoCodeRequest request) {
        if (promoCodeRepository.findByCodeIgnoreCase(request.code()).isPresent()) {
            throw new com.example.shopupu.common.exception.ConflictException("Promo code already exists");
        }
        PromoCode promo = PromoCode.builder()
                .code(request.code().trim().toUpperCase())
                .promoType(request.promoType())
                .value(request.value() == null ? BigDecimal.ZERO : request.value())
                .minOrderAmount(request.minOrderAmount())
                .startsAt(request.startsAt())
                .endsAt(request.endsAt())
                .maxRedemptions(request.maxRedemptions())
                .perUserLimit(request.perUserLimit() == null ? 1 : request.perUserLimit())
                .enabled(request.enabled() == null || request.enabled())
                .build();
        return promoCodeRepository.save(promo);
    }

    @Transactional
    public PromoCode setEnabled(Long id, boolean enabled) {
        PromoCode promo = promoCodeRepository.findById(id)
                .orElseThrow(() -> new com.example.shopupu.common.exception.ResourceNotFoundException("Promo code not found"));
        promo.setEnabled(enabled);
        return promoCodeRepository.save(promo);
    }

    @Transactional(readOnly = true)
    public boolean isFreeShipping(String code) {
        return promoCodeRepository.findByCodeIgnoreCase(code)
                .map(promo -> promo.getPromoType() == PromoCode.Type.FREE_SHIPPING)
                .orElse(false);
    }

    private String normalize(String code) {
        if (code == null || code.isBlank()) {
            throw new BusinessRuleException("Promo code is required");
        }
        return code.trim();
    }
}
