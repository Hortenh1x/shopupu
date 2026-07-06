package com.example.shopupu.identity.repository;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.entity.UserAddress;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAddressRepository extends JpaRepository<UserAddress, Long> {

    List<UserAddress> findByUserOrderByDefaultAddressDescCreatedAtAsc(User user);

    Optional<UserAddress> findByIdAndUser(Long id, User user);

    @Modifying
    @Query("update UserAddress a set a.defaultAddress = false where a.user = :user and a.defaultAddress = true")
    int clearDefault(@Param("user") User user);

    void deleteByUser(User user);
}
