package com.example.shopupu.identity.service;

import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.dto.WishlistEntryResponse;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.entity.WishlistItem;
import com.example.shopupu.identity.repository.WishlistItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistItemRepository wishlistItemRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public Page<WishlistEntryResponse> getWishlist(User user, Pageable pageable) {
        return wishlistItemRepository.findByUser(user, pageable).map(this::toResponse);
    }

    @Transactional
    public void add(User user, Long productId) {
        Product product = productRepository.findById(productId)
                .filter(p -> !p.isDeleted())
                .orElseThrow(() -> new ResourceNotFoundException("Product with id " + productId + " not found"));
        if (wishlistItemRepository.existsByUserAndProduct_Id(user, productId)) {
            return; // idempotent
        }
        wishlistItemRepository.save(WishlistItem.builder().user(user).product(product).build());
    }

    @Transactional
    public void remove(User user, Long productId) {
        wishlistItemRepository.deleteByUserAndProduct_Id(user, productId);
    }

    private WishlistEntryResponse toResponse(WishlistItem item) {
        Product product = item.getProduct();
        return new WishlistEntryResponse(
                product.getId(),
                product.getTitle(),
                product.getSlug(),
                product.getPrice(),
                product.getOldPrice(),
                product.getBrand() != null ? product.getBrand().getName() : null,
                Boolean.TRUE.equals(product.getEnabled()) && !product.isDeleted(),
                item.getCreatedAt()
        );
    }
}
