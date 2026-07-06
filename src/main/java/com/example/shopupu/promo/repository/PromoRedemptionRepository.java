package com.example.shopupu.promo.repository;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.promo.entity.PromoCode;
import com.example.shopupu.promo.entity.PromoRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PromoRedemptionRepository extends JpaRepository<PromoRedemption, Long> {

    long countByPromoAndUser(PromoCode promo, User user);
}
