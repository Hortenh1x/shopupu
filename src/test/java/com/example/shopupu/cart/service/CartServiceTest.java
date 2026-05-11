package com.example.shopupu.cart.service;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * describes the CartServiceTest test class.
 */
@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    private CartService cartService;
    private User user;
    private Cart cart;
    private Product product;

    // handles setUp.
    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, productRepository, userRepository);
        user = User.builder().id(1L).email("user@example.com").build();
        cart = Cart.builder().id(10L).user(user).items(new ArrayList<>()).build();
        product = product(100L, true, 10);
    }

    // handles getOrCreateCart.
    @Test
    void getOrCreateCartReturnsExistingCart() {
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));

        assertSame(cart, cartService.getOrCreateCart("user@example.com"));
    }

    // handles getOrCreateCart.
    @Test
    void getOrCreateCartCreatesCartWhenMissing() {
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(user));
        when(cartRepository.save(any(Cart.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Cart created = cartService.getOrCreateCart("user@example.com");

        assertSame(user, created.getUser());
        verify(cartRepository).save(any(Cart.class));
    }

    // handles getOrCreateCart.
    @Test
    void getOrCreateCartRejectsMissingUser() {
        when(cartRepository.findByUser_Email("missing@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.getOrCreateCart("missing@example.com"));
    }

    // handles addItem.
    @Test
    void addItemCreatesNewCartItemAndReturnsTotals() {
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart), Optional.of(cart));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product));
        when(cartItemRepository.findByCart_IdAndProduct_Id(10L, 100L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            cart.getItems().add(item);
            return item;
        });

        var response = cartService.addItem("user@example.com", 100L, 2);

        assertEquals(2, response.totalItems());
        assertEquals(new BigDecimal("20.00"), response.subtotal());
    }

    // handles addItem.
    @Test
    void addItemRejectsInvalidQuantityDisabledProductAndInsufficientStock() {
        assertThrows(BusinessRuleException.class, () -> cartService.addItem("user@example.com", 100L, 0));

        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product(100L, false, 10)));
        assertThrows(BusinessRuleException.class, () -> cartService.addItem("user@example.com", 100L, 1));

        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(productRepository.findById(100L)).thenReturn(Optional.of(product(100L, true, 1)));
        assertThrows(BusinessRuleException.class, () -> cartService.addItem("user@example.com", 100L, 2));
    }

    // handles setQuantity.
    @Test
    void setQuantityUpdatesOrDeletesExistingItem() {
        CartItem item = CartItem.builder().cart(cart).product(product).quantity(1).build();
        cart.getItems().add(item);
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart), Optional.of(cart), Optional.of(cart), Optional.of(cart));
        when(cartItemRepository.findByCart_IdAndProduct_Id(10L, 100L)).thenReturn(Optional.of(item), Optional.of(item));

        var updated = cartService.setQuantity("user@example.com", 100L, 3);
        assertEquals(3, updated.totalItems());

        var removed = cartService.setQuantity("user@example.com", 100L, 0);
        verify(cartItemRepository).delete(item);
        assertEquals(3, removed.totalItems());
    }

    // handles removeItem.
    @Test
    void removeItemDeletesItemByCartAndProduct() {
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart), Optional.of(cart));

        cartService.removeItem("user@example.com", 100L);

        verify(cartItemRepository).deleteByCart_IdAndProduct_Id(10L, 100L);
    }

    // handles clear.
    @Test
    void clearRemovesCartItems() {
        cart.getItems().add(CartItem.builder().cart(cart).product(product).quantity(1).build());
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart), Optional.of(cart));

        var response = cartService.clear("user@example.com");

        assertEquals(0, response.totalItems());
        verify(cartRepository).save(cart);
    }

    // handles getCart.
    @Test
    void getCartReturnsTotals() {
        cart.getItems().add(CartItem.builder().cart(cart).product(product).quantity(2).build());
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));

        var response = cartService.getCart("user@example.com");

        assertEquals(2, response.totalItems());
        assertEquals(new BigDecimal("20.00"), response.subtotal());
    }

    private Product product(Long id, boolean enabled, int stock) {
        Category category = new Category("Phones", "phones", null, null);
        category.setId(1L);
        Product product = new Product("Phone", "desc", new BigDecimal("10.00"), "sku-" + id, stock, category);
        product.setId(id);
        product.setEnabled(enabled);
        return product;
    }
}
