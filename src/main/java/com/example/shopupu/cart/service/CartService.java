package com.example.shopupu.cart.service;

import com.example.shopupu.cart.dto.CartDtos.*;
import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * RU: Бизнес-логика корзины:
 *  - получить/создать корзину пользователя
 *  - добавить/обновить/удалить позиции
 *  - расчёт сумм
 *
 * EN: Cart business logic:
 *  - get/create user's cart
 *  - add/update/remove items
 *  - compute totals
 */
@Service
@RequiredArgsConstructor
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // RU: Получить корзину пользователя, если нет — создать
    // EN: Get a user's cart; create if missing
    @Transactional
    public Cart getOrCreateCart(String userEmail) {
        return cartRepository.findByUser_Email(userEmail)
                .orElseGet(() -> {
                    User user = userRepository.findByEmail(userEmail)
                            .orElseThrow(() -> new IllegalArgumentException("User: " + userEmail + "not found"));
                    Cart cart = Cart.builder().user(user).build();
                    return cartRepository.save(cart);
                });
    }

    // RU: Добавить позицию (если товар уже есть — увеличить количество)
    // EN: Add item (increment quantity if product already exists)
    @Transactional
    public CartResponse addItem(String userEmail, Long productId, Integer quantity) {
        if (quantity == null || quantity <= 0)
            throw new IllegalArgumentException("Quantity must be more than 0");

        Cart cart = getOrCreateCart(userEmail);

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("Product: " + productId + " not found"));

        CartItem cartItem = cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElse(null);
        if (cartItem == null) {
            cartItem = CartItem.builder()
                    .cart(cart)
                    .product(product)
                    .quantity(quantity)
                    .build();
        } else {
            cartItem.setQuantity(cartItem.getQuantity() + quantity); // RU: наращиваем / EN: increment
        }

        // (опционально) проверка склада product.getStock()
        // Optional: stock check

        cartItemRepository.save(cartItem);
        return toResponse(cartRepository.findByUser_Email(userEmail).orElseThrow());
    }

    // RU: Установить точное количество; если 0 — удалить
    // EN: Set exact quantity; if 0 — remove
    @Transactional
    public CartResponse setQuantity(String userEmail, Long productId, Integer quantity) {
        if (quantity == null || quantity < 0)
            throw new IllegalArgumentException("Quantity must be more than 0");

        Cart cart = getOrCreateCart(userEmail);

        CartItem cartItem = cartItemRepository.findByCart_IdAndProduct_Id(cart.getId(), productId)
                .orElseThrow(() -> new IllegalArgumentException("Item not in cart: product " + productId));

        if (quantity == 0) {
            cartItemRepository.delete(cartItem);
        } else {
            cartItem.setQuantity(quantity);
            cartItemRepository.save(cartItem);
        }

        return toResponse(cartRepository.findByUser_Email(userEmail).orElseThrow());
    }

    // RU: Удалить позицию
    // EN: Remove item
    @Transactional
    public CartResponse removeItem(String userEmail, Long productId) {
        Cart cart = getOrCreateCart(userEmail);
        cartItemRepository.deleteByCart_IdAndProduct_Id(cart.getId(), productId);
        return toResponse(cartRepository.findByUser_Email(userEmail).orElseThrow());
    }

    // RU: Очистить корзину
    // EN: Clear cart
    @Transactional
    public CartResponse clear(String userEmail) {
        Cart cart = getOrCreateCart(userEmail);
        // orphanRemoval=true + очистка списка тоже сработала бы,
        // но удалим напрямую через репозиторий для простоты.
        // orphanRemoval=true would also work by clearing list, but repo delete is simple.
        cart.getItems().clear();
        cartRepository.save(cart);
        return toResponse(cartRepository.findByUser_Email(userEmail).orElseThrow());
    }

    // RU: Получить текущее состояние корзины
    // EN: Get current cart state
    @Transactional
    public CartResponse getCart(String userEmail) {
        Cart cart = getOrCreateCart(userEmail);
        return toResponse(cart);
    }

    // RU/EN: Преобразуем Cart → CartResponse (с подсчётом сумм)
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
}