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
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class CartService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductVariantRepository variantRepository;
    private final InventoryService inventoryService;
    private final UserRepository userRepository;

    /** Cart owner: an authenticated user (email) or an anonymous guest (token). */
    public record CartKey(String email, String guestToken) {
        public static CartKey user(String email) {
            return new CartKey(email, null);
        }

        public static CartKey guest(String token) {
            return new CartKey(null, token);
        }

        public boolean isUser() {
            return email != null;
        }
    }

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
    public Cart getOrCreateGuestCart(String guestToken) {
        if (guestToken != null && !guestToken.isBlank()) {
            var existing = cartRepository.findByGuestToken(guestToken);
            if (existing.isPresent()) {
                return existing.get();
            }
        }
        Cart cart = Cart.builder().guestToken(newGuestToken()).build();
        return cartRepository.save(cart);
    }

    @Transactional
    public CartResponse addItem(CartKey key, Long variantId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessRuleException("Quantity must be more than 0");
        }

        Cart cart = resolveCart(key, true);
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
        return reload(cart);
    }

    @Transactional
    public CartResponse setQuantity(CartKey key, Long variantId, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new BusinessRuleException("Quantity must be 0 or more");
        }

        Cart cart = resolveCart(key, false);
        CartItem cartItem = cartItemRepository.findByCart_IdAndVariant_Id(cart.getId(), variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not in cart: variant " + variantId));

        if (quantity == 0) {
            cartItemRepository.delete(cartItem);
        } else {
            validateVariantCanBeAdded(cartItem.getVariant(), quantity);
            cartItem.setQuantity(quantity);
            cartItemRepository.save(cartItem);
        }

        return reload(cart);
    }

    @Transactional
    public CartResponse removeItem(CartKey key, Long variantId) {
        Cart cart = resolveCart(key, false);
        cartItemRepository.deleteByCart_IdAndVariant_Id(cart.getId(), variantId);
        return reload(cart);
    }

    @Transactional
    public CartResponse clear(CartKey key) {
        Cart cart = resolveCart(key, false);
        cart.getItems().clear();
        cartRepository.save(cart);
        return reload(cart);
    }

    @Transactional
    public CartResponse getCart(CartKey key) {
        if (!key.isUser() && (key.guestToken() == null || key.guestToken().isBlank())) {
            // anonymous browsing without a cart yet: nothing to persist
            return new CartResponse(List.of(), 0, BigDecimal.ZERO, null);
        }
        return toResponse(resolveCart(key, true));
    }

    /** Convenience for authenticated flows (promo validation, tests). */
    @Transactional
    public CartResponse getCart(String userEmail) {
        return getCart(CartKey.user(userEmail));
    }

    /**
     * Merges the guest cart into the user's cart at login (CART-02).
     * Quantities are summed; items that no longer fit availability are dropped.
     */
    @Transactional
    public void mergeGuestCart(String guestToken, String userEmail) {
        if (guestToken == null || guestToken.isBlank()) {
            return;
        }
        Cart guestCart = cartRepository.findByGuestToken(guestToken).orElse(null);
        if (guestCart == null || guestCart.getItems().isEmpty()) {
            if (guestCart != null) {
                cartRepository.delete(guestCart);
            }
            return;
        }

        CartKey userKey = CartKey.user(userEmail);
        for (CartItem item : List.copyOf(guestCart.getItems())) {
            try {
                addItem(userKey, item.getVariant().getId(), item.getQuantity());
            } catch (BusinessRuleException e) {
                log.info("Skipped merging cart item variant={} for {}: {}",
                        item.getVariant().getId(), userEmail, e.getMessage());
            }
        }
        cartRepository.delete(guestCart);
    }

    private Cart resolveCart(CartKey key, boolean createIfMissing) {
        if (key.isUser()) {
            return getOrCreateCart(key.email());
        }
        if (createIfMissing) {
            return getOrCreateGuestCart(key.guestToken());
        }
        if (key.guestToken() == null || key.guestToken().isBlank()) {
            throw new ResourceNotFoundException("Cart not found");
        }
        return cartRepository.findByGuestToken(key.guestToken())
                .orElseThrow(() -> new ResourceNotFoundException("Cart not found"));
    }

    private CartResponse reload(Cart cart) {
        // re-read through the owner lookup: it carries the items entity graph
        Cart fresh = cart.getUser() != null
                ? cartRepository.findByUser_Email(cart.getUser().getEmail()).orElse(cart)
                : cartRepository.findByGuestToken(cart.getGuestToken()).orElse(cart);
        return toResponse(fresh);
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

        return new CartResponse(items, totalItems, subtotal, cart.getGuestToken());
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

    private String newGuestToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
