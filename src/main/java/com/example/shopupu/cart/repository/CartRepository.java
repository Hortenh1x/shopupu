package com.example.shopupu.cart.repository;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.identity.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * describes the CartRepository interface.
 */
public interface CartRepository extends JpaRepository<Cart, Long> {



    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findByUser_Email(String email);
    Optional<Cart> findByUser(User user);

    boolean existsByUser_Email(String email);
}