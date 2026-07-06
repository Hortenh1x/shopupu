package com.example.shopupu.cart.repository;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {

    Optional<CartItem> findByCart_IdAndVariant_Id(Long cartId, Long variantId);

    void deleteByCart_IdAndVariant_Id(Long cartId, Long variantId);

    long countByCart_Id(Long cartId);

    @EntityGraph(attributePaths = {"variant", "variant.product"})
    List<CartItem> findByCart(Cart cart);
}
