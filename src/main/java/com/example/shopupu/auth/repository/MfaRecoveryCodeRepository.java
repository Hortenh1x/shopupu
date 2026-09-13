package com.example.shopupu.auth.repository;

import com.example.shopupu.auth.entity.MfaRecoveryCode;
import java.time.Instant;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface MfaRecoveryCodeRepository extends JpaRepository<MfaRecoveryCode, Long> {
    @Modifying
    @Query("update MfaRecoveryCode c set c.usedAt = :now where c.userId = :userId and c.codeHash = :hash and c.usedAt is null")
    int consume(@Param("userId") Long userId, @Param("hash") String hash, @Param("now") Instant now);
    @Modifying
    @Query("delete from MfaRecoveryCode c where c.userId = :userId")
    int deleteForUser(@Param("userId") Long userId);
}
