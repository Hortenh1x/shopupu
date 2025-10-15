package com.example.shopupu.cart.repository;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.identity.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CartRepository extends JpaRepository<Cart, Long> {

    // RU: Находим корзину по email пользователя + сразу грузим items и product (EntityGraph)
    // EN: Find cart by user email + eagerly load items.product (EntityGraph)
    @EntityGraph(attributePaths = {"items", "items.product"})
    Optional<Cart> findByUser_Email(String email);
    Optional<Cart> findByUser(User user);

    boolean existsByUser_Email(String email);
}