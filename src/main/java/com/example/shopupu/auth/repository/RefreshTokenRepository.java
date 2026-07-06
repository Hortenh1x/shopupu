package com.example.shopupu.auth.repository;

import com.example.shopupu.auth.entity.RefreshToken;
import com.example.shopupu.identity.entity.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    void deleteByUser(User user);

    @Modifying
    @Query("update RefreshToken rt set rt.revoked = true where rt.user = :user and rt.revoked = false")
    int revokeAllByUser(@Param("user") User user);

    @Modifying
    @Query("delete from RefreshToken rt where rt.expiresAt < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
