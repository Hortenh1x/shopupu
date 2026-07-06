package com.example.shopupu.cart.repository;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.identity.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartRepository extends JpaRepository<Cart, Long> {

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    Optional<Cart> findByUser_Email(String email);

    @EntityGraph(attributePaths = {"items", "items.variant", "items.variant.product"})
    Optional<Cart> findByGuestToken(String guestToken);

    Optional<Cart> findByUser(User user);

    boolean existsByUser_Email(String email);
}
