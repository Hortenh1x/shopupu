package com.example.shopupu.cart.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.catalog.repository.ProductVariantRepository;
import com.example.shopupu.inventory.service.InventoryService;
import com.example.shopupu.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Guest cart lines against a real persistence context. The unit tests could
 * not see this: a removed CartItem still referenced from the cart's cascade=ALL
 * collection was re-persisted at flush, so DELETE answered with the line still
 * present and a later GET still had it; a freshly added line was missing from
 * the reply for the same reason (mapped from the stale collection).
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CartLinesIT extends PostgresContainerSupport {

    @Autowired
    private CartService cartService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Test
    void addAndRemoveAreReflectedInTheReplyAndInTheDatabase() {
        ProductVariant first = variant("cart-lines-a");
        ProductVariant second = variant("cart-lines-b");

        // a brand-new guest: the first reply must already carry the line and the token
        var created = cartService.addItem(CartService.CartKey.guest(null), first.getId(), 2);
        assertEquals(List.of(first.getId()), variantIds(created));
        assertEquals(2, created.totalItems());
        CartService.CartKey guest = CartService.CartKey.guest(created.guestToken());

        var extended = cartService.addItem(guest, second.getId(), 1);
        assertEquals(List.of(first.getId(), second.getId()), variantIds(extended));
        assertEquals(3, extended.totalItems());

        var afterRemove = cartService.removeItem(guest, first.getId());
        assertEquals(List.of(second.getId()), variantIds(afterRemove));
        assertEquals(List.of(second.getId()), variantIds(cartService.getCart(guest)),
                "the removed line must be gone from the database, not only from the reply");

        var afterZero = cartService.setQuantity(guest, second.getId(), 0);
        assertTrue(afterZero.items().isEmpty());
        assertTrue(cartService.getCart(guest).items().isEmpty(), "quantity 0 must delete the line for real");
    }

    private List<Long> variantIds(com.example.shopupu.cart.dto.CartResponse response) {
        return response.items().stream().map(item -> item.variantId()).sorted().toList();
    }

    private ProductVariant variant(String prefix) {
        Category category = categoryRepository.save(
                new Category("Cart lines", prefix + "-" + System.nanoTime(), null, null));
        Product product = new Product("Cart Lines Tee", prefix + "-tee-" + System.nanoTime(),
                "test", new BigDecimal("19.99"), category);
        product.setEnabled(true);
        product = productRepository.save(product);
        ProductVariant variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku(prefix.toUpperCase() + "-" + System.nanoTime())
                .size("M")
                .color("black")
                .price(new BigDecimal("19.99"))
                .enabled(true)
                .build());
        inventoryService.setStock(variant.getId(), 10, "test:seed");
        return variant;
    }
}
