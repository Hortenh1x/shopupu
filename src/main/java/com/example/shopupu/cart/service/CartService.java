package com.example.shopupu.cart.service;

import com.example.shopupu.cart.dto.CartItemDto;
import com.example.shopupu.cart.dto.CartResponse;
import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;


@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
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
    public CartResponse addItem(String userEmail, Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessRuleException("Quantity must be more than 0");
        }

        Cart cart = getOrCreateCart(userEmail);
        Product product = findProduct(productId);
        validateProductCanBeAdded(product, quantity);

        CartItem cartItem = cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElse(null);
        if (cartItem == null) {
            cartItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .build();
        } else {
            validateProductCanBeAdded(product, cartItem.getQuantity() + quantity);
            cartItem.setQuantity(cartItem.getQuantity() + quantity);
        }

        cartItemRepository.save(cartItem);
        return reloadCartResponse(userEmail);
    }

    @Transactional
    public CartResponse setQuantity(String userEmail, Long productId, Integer quantity) {
        if (quantity == null || quantity < 0) {
            throw new BusinessRuleException("Quantity must be 0 or more");
        }

        Cart cart = getOrCreateCart(userEmail);
        CartItem cartItem = cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not in cart: product " + productId));

        if (quantity == 0) {
            cartItemRepository.delete(cartItem);
        } else {
            validateProductCanBeAdded(cartItem.getProduct(), quantity);
            cartItem.setQuantity(quantity);
            cartItemRepository.save(cartItem);
        }

        return reloadCartResponse(userEmail);
    }

    @Transactional
    public CartResponse removeItem(String userEmail, Long productId) {
        Cart cart = getOrCreateCart(userEmail);
        cartItemRepository.deleteByCart_IdAndProduct_Id(cart.getId(), productId);
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
            BigDecimal price = cartItem.getProduct().getPrice();
            BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(cartItem.getQuantity()))
                    .setScale(2, RoundingMode.HALF_UP);

            subtotal = subtotal.add(lineTotal).setScale(2, RoundingMode.HALF_UP);
            totalItems += cartItem.getQuantity();

            items.add(new CartItemDto(
                    cartItem.getProduct().getId(),
                    cartItem.getProduct().getTitle(),
                    price,
                    cartItem.getQuantity(),
                    lineTotal
            ));
        }

        return new CartResponse(items, totalItems, subtotal);
    }

    private void validateProductCanBeAdded(Product product, int requestedQuantity) {
        if (!Boolean.TRUE.equals(product.getEnabled())) {
            throw new BusinessRuleException("Product is disabled: " + product.getId());
        }
        if (product.getStock() == null || product.getStock() < requestedQuantity) {
            throw new BusinessRuleException("Not enough stock for product: " + product.getId());
        }
    }

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product: " + productId + " not found"));
    }

    private CartResponse reloadCartResponse(String userEmail) {
        Cart cart = cartRepository.findByUser_Email(userEmail).orElseThrow();
        return toResponse(cart);
    }
}
