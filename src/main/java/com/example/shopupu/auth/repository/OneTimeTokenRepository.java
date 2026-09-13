package com.example.shopupu.auth.repository;

import com.example.shopupu.auth.entity.OneTimeToken;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OneTimeTokenRepository extends JpaRepository<OneTimeToken, Long> {

    Optional<OneTimeToken> findByTokenHashAndPurpose(String tokenHash, OneTimeToken.Purpose purpose);

    @Query("select t.user.id from OneTimeToken t where t.tokenHash = :hash and t.purpose = :purpose")
    Optional<Long> findUserId(@Param("hash") String hash, @Param("purpose") OneTimeToken.Purpose purpose);

    @Modifying
    @Query("""
            update OneTimeToken t set t.usedAt = :now where t.tokenHash = :hash
            and t.purpose = :purpose and t.usedAt is null and t.expiresAt > :now""")
    int consume(@Param("hash") String hash, @Param("purpose") OneTimeToken.Purpose purpose,
                @Param("now") Instant now);

    @Modifying
    @Query("""
            update OneTimeToken t set t.usedAt = :now
            where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null""")
    int invalidateAllFor(@Param("userId") Long userId,
                         @Param("purpose") OneTimeToken.Purpose purpose,
                         @Param("now") Instant now);

    @Modifying
    @Query("delete from OneTimeToken t where t.expiresAt < :cutoff")
    int deleteAllExpiredBefore(@Param("cutoff") Instant cutoff);
}
