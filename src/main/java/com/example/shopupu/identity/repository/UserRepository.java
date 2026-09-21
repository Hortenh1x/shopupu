package com.example.shopupu.identity.repository;

import com.example.shopupu.identity.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
/**
 * describes the UserRepository interface.
 */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);

    @org.springframework.data.jpa.repository.Query("select u.id from User u where u.email = :email")
    Optional<Long> findIdByEmail(@org.springframework.data.repository.query.Param("email") String email);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);

    /** Live accounts without ADMIN/MANAGER whose last sign-in is older than the cutoff; null = unknown, never a candidate. */
    @org.springframework.data.jpa.repository.Query("""
            select u.id from User u where u.deletedAt is null and u.lastLoginAt < :cutoff
            and not exists (select 1 from User p join p.roles r where p.id = u.id and r.name in ('ADMIN', 'MANAGER'))
            order by u.id""")
    java.util.List<Long> findInactiveCustomerIds(
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            org.springframework.data.domain.Pageable pageable);

    @org.springframework.data.jpa.repository.Query("select u.id from User u where u.deletedAt < :cutoff order by u.id")
    java.util.List<Long> findErasedBefore(
            @org.springframework.data.repository.query.Param("cutoff") java.time.Instant cutoff,
            org.springframework.data.domain.Pageable pageable);
}
