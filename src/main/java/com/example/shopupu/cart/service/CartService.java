package com.example.shopupu.cart.service;

import com.example.shopupu.cart.dto.CartItemDto;
import com.example.shopupu.cart.dto.CartResponse;
import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.catalog.repository.ProductVariantRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.OutOfStockException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.inventory.service.InventoryService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;

    @Transactional
    public Cart getOrCreateCart(String userEmail) {
        return cartRepository.findByUser_Email(userEmail)
                .orElseGet(() -> {
                    User user = userRepository.findByEmail(userEmail)
                            .orElseThrow(() -> new ResourceNotFoundException("User: " + userEmail + " not found"));
                    Cart cart = Cart.builder().user(user).build();
                    return cartRepository.save(cart);
                });
    }

    @Transactional
    public CartResponse addItem(String userEmail, Long variantId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessRuleException("Quantity must be more than 0");
        }

        Cart cart = getOrCreateCart(userEmail);
        ProductVariant variant = findVariant(variantId);
        validateVariantCanBeAdded(variant, quantity);

        CartItem cartItem = cartItemRepository.findByCart_IdAndVariant_Id(cart.getId(), variantId)
                .orElse(null);
        if (cartItem == null) {
            cartItem = CartItem.builder()
                    .cart(cart)
                    .variant(variant)
                    .quantity(quantity)
                    .build();
        } else {
            validateVariantCanBeAdded(variant, cartItem.getQuantity() + quantity);
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
        }

        cartItemRepository.save(cartItem);
        return reloadCartResponse(userEmail);
    }

    @Transactional
    public CartResponse setQuantity(String userEmail, Long variantId, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new BusinessRuleException("Quantity must be 0 or more");
        }

        Cart cart = getOrCreateCart(userEmail);
        CartItem cartItem = cartItemRepository.findByCart_IdAndVariant_Id(cart.getId(), variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not in cart: variant " + variantId));

        if (quantity == 0) {
            cartItemRepository.delete(cartItem);
        } else {
            validateVariantCanBeAdded(cartItem.getVariant(), quantity);
            cartItem.setQuantity(quantity);
            cartItemRepository.save(cartItem);
        }

        return reloadCartResponse(userEmail);
    }

    @Transactional
    public CartResponse removeItem(String userEmail, Long variantId) {
        Cart cart = getOrCreateCart(userEmail);
        cartItemRepository.deleteByCart_IdAndVariant_Id(cart.getId(), variantId);
        return reloadCartResponse(userEmail);
    }

    @Transactional
    public CartResponse clear(String userEmail) {
        Cart cart = getOrCreateCart(userEmail);
        cart.getItems().clear();
        cartRepository.save(cart);
        return reloadCartResponse(userEmail);
    }

    @Transactional
    public CartResponse getCart(String userEmail) {
        Cart cart = getOrCreateCart(userEmail);
        return toResponse(cart);
    }

    private CartResponse toResponse(Cart cart) {
        List<CartItemDto> items = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        int totalItems = 0;

        for (CartItem cartItem : cart.getItems()) {
            ProductVariant variant = cartItem.getVariant();
            BigDecimal price = variant.getPrice();
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            subtotal = subtotal.add(lineTotal).setScale(2, RoundingMode.HALF_UP);
            totalItems += cartItem.getQuantity();

            Product product = variant.getProduct();
            items.add(new CartItemDto(
                    variant.getId(),
                    product.getId(),
                    product.getTitle(),
                    variant.getSku(),
                    variant.getSize(),
                    variant.getColor(),
                    price,
                    cartItem.getQuantity(),
                    lineTotal
            ));
        }

        return new CartResponse(items, totalItems, subtotal);
    }

    private void validateVariantCanBeAdded(ProductVariant variant, int requestedQuantity) {
        Product product = variant.getProduct();
        if (!Boolean.TRUE.equals(variant.getEnabled()) || !Boolean.TRUE.equals(product.getEnabled())
                || product.isDeleted()) {
            throw new BusinessRuleException("Product is not available: " + product.getId());
        }
        int available = inventoryService.availableFor(variant.getId());
        if (available < requestedQuantity) {
            throw new OutOfStockException("Not enough stock for variant: " + variant.getId());
        }
    }

    private ProductVariant findVariant(Long variantId) {
        return variantRepository.findWithProductById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant: " + variantId + " not found"));
    }

    private CartResponse reloadCartResponse(String userEmail) {
        Cart cart = cartRepository.findByUser_Email(userEmail).orElseThrow();
        return toResponse(cart);
    }
}
