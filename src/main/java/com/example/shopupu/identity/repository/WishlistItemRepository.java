package com.example.shopupu.identity.repository;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.entity.WishlistItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WishlistItemRepository extends JpaRepository<WishlistItem, Long> {

    @EntityGraph(attributePaths = {"product", "product.brand", "product.category"})
    Page<WishlistItem> findByUser(User user, Pageable pageable);

    boolean existsByUserAndProduct_Id(User user, Long productId);

    void deleteByUserAndProduct_Id(User user, Long productId);

    void deleteByUser(User user);
}
