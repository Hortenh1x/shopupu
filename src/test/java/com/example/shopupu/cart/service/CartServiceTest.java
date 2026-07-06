package com.example.shopupu.cart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.catalog.entity.Category;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private ProductVariantRepository variantRepository;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private UserRepository userRepository;

    private CartService cartService;
    private User user;
    private Cart cart;
    private Product product;
    private ProductVariant variant;

    // handles setUp.
    @BeforeEach
    void setUp() {
        cartService = new CartService(cartRepository, cartItemRepository, variantRepository, inventoryService, userRepository);
        user = User.builder().id(1L).email("user@example.com").build();
        cart = Cart.builder().id(10L).user(user).items(new ArrayList<>()).build();
        product = product(1L, true, false);
        variant = variant(100L, product, true);
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
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(variantRepository.findWithProductById(100L)).thenReturn(Optional.of(variant));
        when(inventoryService.availableFor(100L)).thenReturn(10);
        when(cartItemRepository.findByCart_IdAndVariant_Id(10L, 100L)).thenReturn(Optional.empty());
        when(cartItemRepository.save(any(CartItem.class))).thenAnswer(invocation -> {
            CartItem item = invocation.getArgument(0);
            cart.getItems().add(item);
            return item;
        });

        var response = cartService.addItem("user@example.com", 100L, 2);

        assertEquals(2, response.totalItems());
        assertEquals(new BigDecimal("20.00"), response.subtotal());
        var itemDto = response.items().get(0);
        assertEquals(100L, itemDto.variantId());
        assertEquals(1L, itemDto.productId());
        assertEquals("SKU-100", itemDto.sku());
        assertEquals("M", itemDto.size());
        assertEquals("Black", itemDto.color());
        assertEquals(new BigDecimal("20.00"), itemDto.lineTotal());
    }

    // handles addItem.
    @Test
    void addItemIncrementsQuantityOfExistingItem() {
        CartItem existing = CartItem.builder().cart(cart).variant(variant).quantity(1).build();
        cart.getItems().add(existing);
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(variantRepository.findWithProductById(100L)).thenReturn(Optional.of(variant));
        when(inventoryService.availableFor(100L)).thenReturn(10);
        when(cartItemRepository.findByCart_IdAndVariant_Id(10L, 100L)).thenReturn(Optional.of(existing));
        when(cartItemRepository.save(existing)).thenReturn(existing);

        var response = cartService.addItem("user@example.com", 100L, 2);

        assertEquals(3, existing.getQuantity());
        assertEquals(3, response.totalItems());
        assertEquals(new BigDecimal("30.00"), response.subtotal());
    }

    // handles addItem.
    @Test
    void addItemRejectsInvalidQuantity() {
        assertThrows(BusinessRuleException.class, () -> cartService.addItem("user@example.com", 100L, 0));
        assertThrows(BusinessRuleException.class, () -> cartService.addItem("user@example.com", 100L, null));
    }

    // handles addItem.
    @Test
    void addItemRejectsMissingVariant() {
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(variantRepository.findWithProductById(404L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> cartService.addItem("user@example.com", 404L, 1));
    }

    // handles addItem.
    @Test
    void addItemRejectsDisabledVariant() {
        ProductVariant disabledVariant = variant(100L, product, false);
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(variantRepository.findWithProductById(100L)).thenReturn(Optional.of(disabledVariant));

        assertThrows(BusinessRuleException.class, () -> cartService.addItem("user@example.com", 100L, 1));
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // handles addItem.
    @Test
    void addItemRejectsDisabledOrDeletedProduct() {
        ProductVariant variantOfDisabled = variant(100L, product(1L, false, false), true);
        ProductVariant variantOfDeleted = variant(100L, product(1L, true, true), true);
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(variantRepository.findWithProductById(100L))
                .thenReturn(Optional.of(variantOfDisabled), Optional.of(variantOfDeleted));

        assertThrows(BusinessRuleException.class, () -> cartService.addItem("user@example.com", 100L, 1));
        assertThrows(BusinessRuleException.class, () -> cartService.addItem("user@example.com", 100L, 1));
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // handles addItem.
    @Test
    void addItemThrowsOutOfStockWhenAvailableLessThanRequested() {
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(variantRepository.findWithProductById(100L)).thenReturn(Optional.of(variant));
        when(inventoryService.availableFor(100L)).thenReturn(1);

        assertThrows(OutOfStockException.class, () -> cartService.addItem("user@example.com", 100L, 2));
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // handles setQuantity.
    @Test
    void setQuantityUpdatesOrDeletesExistingItem() {
        CartItem item = CartItem.builder().cart(cart).variant(variant).quantity(1).build();
        cart.getItems().add(item);
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_IdAndVariant_Id(10L, 100L)).thenReturn(Optional.of(item));
        when(inventoryService.availableFor(100L)).thenReturn(10);

        var updated = cartService.setQuantity("user@example.com", 100L, 3);
        assertEquals(3, updated.totalItems());

        var removed = cartService.setQuantity("user@example.com", 100L, 0);
        verify(cartItemRepository).delete(item);
        assertEquals(3, removed.totalItems());
    }

    // handles setQuantity.
    @Test
    void setQuantityRejectsNegativeQuantityAndMissingItem() {
        assertThrows(BusinessRuleException.class, () -> cartService.setQuantity("user@example.com", 100L, -1));

        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_IdAndVariant_Id(10L, 100L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> cartService.setQuantity("user@example.com", 100L, 1));
    }

    // handles setQuantity.
    @Test
    void setQuantityThrowsOutOfStockWhenAvailableLessThanRequested() {
        CartItem item = CartItem.builder().cart(cart).variant(variant).quantity(1).build();
        cart.getItems().add(item);
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));
        when(cartItemRepository.findByCart_IdAndVariant_Id(10L, 100L)).thenReturn(Optional.of(item));
        when(inventoryService.availableFor(100L)).thenReturn(2);

        assertThrows(OutOfStockException.class, () -> cartService.setQuantity("user@example.com", 100L, 5));
        verify(cartItemRepository, never()).save(any(CartItem.class));
    }

    // handles removeItem.
    @Test
    void removeItemDeletesItemByCartAndVariant() {
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));

        cartService.removeItem("user@example.com", 100L);

        verify(cartItemRepository).deleteByCart_IdAndVariant_Id(10L, 100L);
    }

    // handles clear.
    @Test
    void clearRemovesCartItems() {
        cart.getItems().add(CartItem.builder().cart(cart).variant(variant).quantity(1).build());
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));

        var response = cartService.clear("user@example.com");

        assertEquals(0, response.totalItems());
        verify(cartRepository).save(cart);
    }

    // handles getCart.
    @Test
    void getCartReturnsTotals() {
        cart.getItems().add(CartItem.builder().cart(cart).variant(variant).quantity(2).build());
        when(cartRepository.findByUser_Email("user@example.com")).thenReturn(Optional.of(cart));

        var response = cartService.getCart("user@example.com");

        assertEquals(2, response.totalItems());
        assertEquals(new BigDecimal("20.00"), response.subtotal());
    }

    private Product product(Long id, boolean enabled, boolean deleted) {
        Category category = new Category("Clothes", "clothes", null, null);
        category.setId(1L);
        Product product = new Product("Hoodie", "hoodie", "desc", new BigDecimal("10.00"), category);
        product.setId(id);
        product.setEnabled(enabled);
        if (deleted) {
            product.setDeletedAt(Instant.now());
        }
        return product;
    }

    private ProductVariant variant(Long id, Product product, boolean enabled) {
        return ProductVariant.builder()
                .id(id)
                .product(product)
                .sku("SKU-" + id)
                .size("M")
                .color("Black")
                .price(new BigDecimal("10.00"))
                .enabled(enabled)
                .build();
    }
}
