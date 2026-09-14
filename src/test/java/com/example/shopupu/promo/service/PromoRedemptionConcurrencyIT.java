package com.example.shopupu.promo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
import com.example.shopupu.common.exception.BusinessRuleException;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.inventory.repository.InventoryRepository;
import com.example.shopupu.inventory.service.InventoryService;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.service.OrderService;
import com.example.shopupu.promo.entity.PromoCode;
import com.example.shopupu.promo.repository.PromoCodeRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * PROMO-03: a promo code with a global limit must hand out exactly that many
 * discounts under concurrent checkouts, and a checkout that loses the
 * redemption race must leave nothing behind — no order, no reservation.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class PromoRedemptionConcurrencyIT extends PostgresContainerSupport {

    private static final int MAX_REDEMPTIONS = 3;
    private static final int CONTENDERS = 6;

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

    @Autowired
    private PromoCodeRepository promoCodeRepository;

    @Autowired
    private JdbcClient jdbc;

    private ProductVariant variant;
    private PromoCode promo;

    @BeforeEach
    void fixtures() {
        Category category = categoryRepository.save(new Category("Tees", "tees-" + System.nanoTime(), null, null));
        Product product = new Product("Promo Tee", "promo-tee-" + System.nanoTime(), "test", new BigDecimal("20.00"), category);
        product.setEnabled(true);
        product = productRepository.save(product);
        variant = variantRepository.save(ProductVariant.builder()
                .product(product)
                .sku("PROMO-TEE-" + System.nanoTime())
                .size("M")
                .color("black")
                .price(new BigDecimal("20.00"))
                .enabled(true)
                .build());
        inventoryService.setStock(variant.getId(), 100, "test:seed");

        promo = promoCodeRepository.save(PromoCode.builder()
                .code("RACE-" + System.nanoTime())
                .promoType(PromoCode.Type.PERCENT)
                .value(new BigDecimal("10"))
                .maxRedemptions(MAX_REDEMPTIONS)
                .perUserLimit(1)
                .enabled(true)
                .build());
    }

    @Test
    void globalRedemptionLimitHoldsUnderConcurrentCheckouts() throws Exception {
        List<User> users = new ArrayList<>();
        for (int i = 0; i < CONTENDERS; i++) {
            User user = user("racer" + i);
            cartWith(user, 1);
            users.add(user);
        }

        ExecutorService executor = Executors.newFixedThreadPool(CONTENDERS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Object>> futures = new ArrayList<>();
        for (User user : users) {
            futures.add(executor.submit((Callable<Object>) () -> {
                start.await(5, TimeUnit.SECONDS);
                try {
                    return orderService.createOrderFromCart(user, null, promo.getCode());
                } catch (Exception e) {
                    return e;
                }
            }));
        }
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(60, TimeUnit.SECONDS));

        List<Object> outcomes = new ArrayList<>();
        for (Future<Object> future : futures) {
            outcomes.add(future.get());
        }
        List<Order> winners = outcomes.stream().filter(Order.class::isInstance).map(Order.class::cast).toList();
        long exhausted = outcomes.stream()
                .filter(BusinessRuleException.class::isInstance)
                .filter(e -> ((BusinessRuleException) e).getMessage().contains("exhausted"))
                .count();

        assertEquals(MAX_REDEMPTIONS, winners.size(), "exactly maxRedemptions checkouts may win, got: " + outcomes);
        assertEquals(CONTENDERS - MAX_REDEMPTIONS, exhausted, "every loser must fail with 'exhausted', got: " + outcomes);
        winners.forEach(order -> assertEquals(0, new BigDecimal("2.00").compareTo(order.getDiscountAmount()),
                "a winning order carries the 10% discount"));

        PromoCode after = promoCodeRepository.findById(promo.getId()).orElseThrow();
        assertEquals(MAX_REDEMPTIONS, after.getRedemptionCount(), "the counter never exceeds the limit");
        assertEquals(MAX_REDEMPTIONS, redemptionRows(), "one redemption row per winning order");

        // A losing checkout rolls back as a whole: its reservation and order must not survive.
        var inventory = inventoryRepository.findByVariant_Id(variant.getId()).orElseThrow();
        assertEquals(MAX_REDEMPTIONS, inventory.getReserved(), "losers must not keep stock reserved");
        assertEquals(MAX_REDEMPTIONS, orderRowsFor(users), "losers must not leave an order behind");
    }

    @Test
    void perUserLimitRejectsASecondCheckoutWithTheSameCode() {
        User repeat = user("repeat");
        cartWith(repeat, 1);
        Order first = orderService.createOrderFromCart(repeat, null, promo.getCode());
        assertEquals(0, new BigDecimal("2.00").compareTo(first.getDiscountAmount()));

        cartWith(repeat, 1);
        BusinessRuleException second = assertThrows(BusinessRuleException.class,
                () -> orderService.createOrderFromCart(repeat, null, promo.getCode()));
        assertTrue(second.getMessage().contains("already used"), second.getMessage());
        assertEquals(1, promoCodeRepository.findById(promo.getId()).orElseThrow().getRedemptionCount());
    }

    private long redemptionRows() {
        return jdbc.sql("select count(*) from promo_redemptions where promo_id = :id")
                .param("id", promo.getId())
                .query(Long.class)
                .single();
    }

    private long orderRowsFor(List<User> users) {
        return jdbc.sql("select count(*) from orders where user_id in (:ids)")
                .param("ids", users.stream().map(User::getId).toList())
                .query(Long.class)
                .single();
    }

    private User user(String prefix) {
        return userRepository.save(User.builder()
                .email(prefix + "-" + System.nanoTime() + "@example.com")
                .passwordHash("test-hash")
                .enabled(true)
                .build());
    }

    private void cartWith(User user, int quantity) {
        Cart cart = cartRepository.findByUser(user)
                .orElseGet(() -> cartRepository.save(Cart.builder().user(user).build()));
        cartItemRepository.save(CartItem.builder().cart(cart).variant(variant).quantity(quantity).build());
    }
}
