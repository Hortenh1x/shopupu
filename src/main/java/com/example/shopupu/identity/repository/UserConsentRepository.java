package com.example.shopupu.identity.repository;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.entity.UserConsent;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserConsentRepository extends JpaRepository<UserConsent, Long> {

    List<UserConsent> findByUserOrderByCreatedAtDesc(User user);
}
