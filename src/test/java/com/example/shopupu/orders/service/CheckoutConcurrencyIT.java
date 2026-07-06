package com.example.shopupu.orders.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.shopupu.cart.entity.Cart;
import com.example.shopupu.cart.entity.CartItem;
import com.example.shopupu.cart.repository.CartItemRepository;
import com.example.shopupu.cart.repository.CartRepository;
import com.example.shopupu.catalog.entity.Category;
import com.example.shopupu.catalog.entity.Product;
import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.catalog.repository.CategoryRepository;
import com.example.shopupu.catalog.repository.ProductRepository;
import com.example.shopupu.catalog.repository.ProductVariantRepository;
import com.example.shopupu.common.exception.OutOfStockException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.inventory.repository.InventoryRepository;
import com.example.shopupu.inventory.service.InventoryService;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.support.PostgresContainerSupport;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TEST-09/INV-03: two concurrent checkouts must never oversell — the atomic
 * reservation UPDATE guarantees only one of them gets the last items.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class CheckoutConcurrencyIT extends PostgresContainerSupport {

    @Autowired
    private OrderService orderService;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductVariantRepository variantRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Test
    void concurrentCheckoutsDoNotOversell() throws Exception {
        Category category = categoryRepository.save(
                new Category("Tees", "tees-" + System.nanoTime(), null, null));

        Product product = new Product("Concurrency Tee", "concurrency-tee-" + System.nanoTime(),
                "test", new BigDecimal("19.99"), category);
        product.setEnabled(true);
        product = productRepository.save(product);

        ProductVariant variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("CONC-TEE-" + System.nanoTime())
                .size("M")
                .color("black")
                .price(new BigDecimal("19.99"))
                .enabled(true)
                .build());

        inventoryService.setStock(variant.getId(), 5, "test:seed");

        User alice = user("alice");
        User bob = user("bob");
        cartWith(alice, variant, 3);
        cartWith(bob, variant, 3);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (User user : List.of(alice, bob)) {
            futures.add(executor.submit((Callable<Object>) () -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    return orderService.createOrderFromCart(user, null);
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));

        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            outcomes.add(future.get());
        }

        long successes = outcomes.stream().filter(Order.class::isInstance).count();
        long outOfStock = outcomes.stream().filter(OutOfStockException.class::isInstance).count();

        assertEquals(1, successes, "exactly one checkout must win the last stock");
        assertEquals(1, outOfStock, "the losing checkout must fail with OutOfStockException, got: " + outcomes);

        var inventory = inventoryRepository.findByVariant_Id(variant.getId()).orElseThrow();
        assertEquals(3, inventory.getReserved(), "only the winning order may hold a reservation");
        assertEquals(5, inventory.getStock());
        assertTrue(inventory.getReserved() <= inventory.getStock());

        Object failure = outcomes.stream().filter(o -> !(o instanceof Order)).findFirst().orElseThrow();
        assertInstanceOf(OutOfStockException.class, failure);
    }

    private User user(String prefix) {
        return userRepository.save(User.builder()
                .email(prefix + "-" + System.nanoTime() + "@example.com")
                .passwordHash("test-hash")
                .enabled(true)
                .build());
    }

    private void cartWith(User user, ProductVariant variant, int quantity) {
        Cart cart = cartRepository.save(Cart.builder().user(user).build());
        cartItemRepository.save(CartItem.builder().cart(cart).variant(variant).quantity(quantity).build());
    }
}
