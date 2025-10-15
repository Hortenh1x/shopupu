package com.example.shopupu.cart.repository;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCart_IdAndProduct_Id(Long cartId, Long productId);

    void deleteByCart_IdAndProduct_Id(Long cartId, Long productId);

    long countByCart_Id(Long cartId);

    List<CartItem> findByCart(Cart cart);
}
