package com.example.shopupu.auth.repository;

import com.example.shopupu.auth.entity.MfaChallenge;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MfaChallengeRepository extends JpaRepository<MfaChallenge, Long> {
    Optional<MfaChallenge> findByTokenHash(String hash);
    @Query("select c.userId from MfaChallenge c where c.tokenHash = :hash")
    Optional<Long> findUserId(@Param("hash") String hash);
    @Modifying
    @Query("update MfaChallenge c set c.usedAt = :now where c.userId = :userId and c.usedAt is null")
    int invalidateForUser(@Param("userId") Long userId, @Param("now") Instant now);
    @Modifying
    @Query("delete from MfaChallenge c where c.expiresAt < :cutoff")
    int deleteExpired(@Param("cutoff") Instant cutoff);
}
