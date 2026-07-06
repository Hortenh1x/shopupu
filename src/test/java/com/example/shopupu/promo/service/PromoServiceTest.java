package com.example.shopupu.promo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.promo.entity.PromoCode;
import com.example.shopupu.promo.repository.PromoCodeRepository;
import com.example.shopupu.promo.repository.PromoRedemptionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PromoServiceTest {

    @Mock
    private PromoCodeRepository promoCodeRepository;

    @Mock
    private PromoRedemptionRepository redemptionRepository;

    @InjectMocks
    private PromoService promoService;

    private final User user = User.builder().id(1L).email("user@example.com").build();

    @Test
    void validateAcceptsActiveCodeWithinLimits() {
        PromoCode promo = promo(PromoCode.Type.PERCENT, new BigDecimal("10"));
        when(promoCodeRepository.findByCodeIgnoreCase("SALE10")).thenReturn(Optional.of(promo));
        when(redemptionRepository.countByPromoAndUser(promo, user)).thenReturn(0L);

        PromoCode result = promoService.validate("SALE10", user, new BigDecimal("100.00"));

        assertEquals(promo, result);
    }

    @Test
    void validateRejectsExpiredDisabledExhaustedAndBelowMinimum() {
        PromoCode expired = promo(PromoCode.Type.PERCENT, new BigDecimal("10"));
        expired.setEndsAt(Instant.now().minusSeconds(60));
        when(promoCodeRepository.findByCodeIgnoreCase("EXPIRED")).thenReturn(Optional.of(expired));
        assertThrows(BusinessRuleException.class,
                () -> promoService.validate("EXPIRED", user, new BigDecimal("100.00")));

        PromoCode disabled = promo(PromoCode.Type.PERCENT, new BigDecimal("10"));
        disabled.setEnabled(false);
        when(promoCodeRepository.findByCodeIgnoreCase("DISABLED")).thenReturn(Optional.of(disabled));
        assertThrows(BusinessRuleException.class,
                () -> promoService.validate("DISABLED", user, new BigDecimal("100.00")));

        PromoCode exhausted = promo(PromoCode.Type.PERCENT, new BigDecimal("10"));
        exhausted.setMaxRedemptions(5);
        exhausted.setRedemptionCount(5);
        when(promoCodeRepository.findByCodeIgnoreCase("GONE")).thenReturn(Optional.of(exhausted));
        assertThrows(BusinessRuleException.class,
                () -> promoService.validate("GONE", user, new BigDecimal("100.00")));

        PromoCode highMinimum = promo(PromoCode.Type.PERCENT, new BigDecimal("10"));
        highMinimum.setMinOrderAmount(new BigDecimal("200.00"));
        when(promoCodeRepository.findByCodeIgnoreCase("BIG")).thenReturn(Optional.of(highMinimum));
        assertThrows(BusinessRuleException.class,
                () -> promoService.validate("BIG", user, new BigDecimal("100.00")));
    }

    @Test
    void validateRejectsSecondUseByTheSameUser() {
        PromoCode promo = promo(PromoCode.Type.PERCENT, new BigDecimal("10"));
        when(promoCodeRepository.findByCodeIgnoreCase("ONCE")).thenReturn(Optional.of(promo));
        when(redemptionRepository.countByPromoAndUser(promo, user)).thenReturn(1L);

        assertThrows(BusinessRuleException.class,
                () -> promoService.validate("ONCE", user, new BigDecimal("100.00")));
    }

    @Test
    void discountComputation() {
        assertEquals(new BigDecimal("10.00"),
                promoService.discountFor(promo(PromoCode.Type.PERCENT, new BigDecimal("10")), new BigDecimal("100.00")));
        assertEquals(new BigDecimal("15.00"),
                promoService.discountFor(promo(PromoCode.Type.FIXED, new BigDecimal("15.00")), new BigDecimal("100.00")));
        // fixed discount never exceeds the subtotal
        assertEquals(new BigDecimal("100.00"),
                promoService.discountFor(promo(PromoCode.Type.FIXED, new BigDecimal("150.00")), new BigDecimal("100.00")));
        assertEquals(BigDecimal.ZERO,
                promoService.discountFor(promo(PromoCode.Type.FREE_SHIPPING, BigDecimal.ZERO), new BigDecimal("100.00")));
    }

    @Test
    void redeemFailsWhenGlobalLimitRaceLost() {
        PromoCode promo = promo(PromoCode.Type.PERCENT, new BigDecimal("10"));
        promo.setId(5L);
        when(redemptionRepository.countByPromoAndUser(promo, user)).thenReturn(0L);
        when(promoCodeRepository.tryRedeem(5L)).thenReturn(0);

        assertThrows(BusinessRuleException.class, () -> promoService.redeem(promo, user, null));
        verify(redemptionRepository, never()).save(any());
    }

    @Test
    void redeemStoresRedemptionOnSuccess() {
        PromoCode promo = promo(PromoCode.Type.PERCENT, new BigDecimal("10"));
        promo.setId(5L);
        when(redemptionRepository.countByPromoAndUser(promo, user)).thenReturn(0L);
        when(promoCodeRepository.tryRedeem(5L)).thenReturn(1);

        promoService.redeem(promo, user, null);

        verify(redemptionRepository).save(any());
    }

    private PromoCode promo(PromoCode.Type type, BigDecimal value) {
        return PromoCode.builder()
                .id(1L)
                .code("SALE10")
                .promoType(type)
                .value(value)
                .perUserLimit(1)
                .enabled(true)
                .build();
    }
}
