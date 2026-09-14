package com.example.shopupu.security;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.inventory.service.InventoryService;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.orders.service.OrderService;
import com.example.shopupu.payments.entity.PaymentStatus;
import com.example.shopupu.payments.repository.PaymentRepository;
import com.example.shopupu.reviews.entity.Review;
import com.example.shopupu.reviews.entity.ReviewStatus;
import com.example.shopupu.reviews.repository.ReviewRepository;
import com.example.shopupu.shipping.entity.ShippingMethod;
import com.example.shopupu.shipping.repository.ShipmentRepository;
import com.example.shopupu.support.PostgresContainerSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * IDOR matrix (AUTH-02 / Q-15): every customer-facing endpoint that addresses an object by id
 * refuses another customer without leaking the object and without a side effect on it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class ObjectAuthorizationIT extends PostgresContainerSupport {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired InventoryService inventory;
    @Autowired CartRepository carts;
    @Autowired CartItemRepository cartItems;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired ShipmentRepository shipments;
    @Autowired ReviewRepository reviews;

    private User owner;
    private User stranger;
    private Product product;
    private Order order;
    private long paymentId;
    private long addressId;
    private long reviewId;

    @BeforeEach
    void fixtures() throws Exception {
        owner = saveUser("owner");
        stranger = saveUser("stranger");
        Category category = categories.save(new Category("Tee", key(), null, null));
        Product p = new Product("Tee", key(), "demo", new BigDecimal("20.00"), category);
        p.setEnabled(true);
        product = products.save(p);
        ProductVariant variant = variants.save(ProductVariant.builder().product(product).sku(key()).size("M")
                .color("black").price(new BigDecimal("20.00")).enabled(true).build());
        inventory.setStock(variant.getId(), 10, "test");
        Cart cart = carts.save(Cart.builder().user(owner).build());
        cartItems.save(CartItem.builder().cart(cart).variant(variant).quantity(1).build());
        order = orderService.createOrderFromCart(owner, key());

        as(owner, post("/api/v1/shipping/method").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order.getId() + ",\"method\":\"LOCAL_PICKUP\"}"))
                .andExpect(status().isOk());
        paymentId = body(as(owner, post("/api/v1/payments").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + order.getId() + "}"))
                .andExpect(status().isCreated())).get("id").asLong();
        addressId = body(as(owner, post("/api/v1/users/me/addresses").contentType(MediaType.APPLICATION_JSON)
                .content(address("Owner Person"))).andExpect(status().is2xxSuccessful())).get("id").asLong();
        as(owner, post("/api/v1/users/me/wishlist/" + product.getId())).andExpect(status().is2xxSuccessful());

        Review review = new Review();
        review.setUser(owner);
        review.setProduct(product);
        review.setOrder(order);
        review.setRating(5);
        review.setBody("Owner review body");
        review.setStatus(ReviewStatus.APPROVED);
        reviewId = reviews.save(review).getId();
    }

    @Test
    void ownerCanReadEveryFixtureSoTheDenialsBelowAreMeaningful() throws Exception {
        as(owner, get("/api/v1/orders/" + order.getId())).andExpect(status().isOk());
        as(owner, get("/api/v1/shipping/" + order.getId())).andExpect(status().isOk());
        as(owner, get("/api/v1/payments/" + paymentId)).andExpect(status().isOk());
        as(owner, get("/api/v1/users/me/addresses")).andExpect(jsonPath("$[0].id").value(addressId));
        as(owner, get("/api/v1/users/me/wishlist")).andExpect(jsonPath("$.content[0].productId").value(product.getId()));
    }

    @Test
    void ordersAndShipmentsOfAnotherCustomerAreInvisibleAndImmutable() throws Exception {
        denied(as(stranger, get("/api/v1/orders/" + order.getId())));
        denied(as(stranger, patch("/api/v1/orders/" + order.getId() + "/cancel")));
        denied(as(stranger, get("/api/v1/shipping/" + order.getId())));
        denied(as(stranger, post("/api/v1/shipping/method").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order.getId() + ",\"method\":\"DHL\"}")));
        denied(as(stranger, post("/api/v1/shipping/address").contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order.getId() + ",\"fullName\":\"Intruder\",\"line1\":\"x\",\"city\":\"x\","
                        + "\"state\":\"x\",\"postalCode\":\"1\",\"country\":\"DE\"}")));
        as(stranger, get("/api/v1/orders")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(order.getOrderNumber()))));

        // Creating the payment in fixtures() moved the order to PENDING_PAYMENT; the stranger's cancel must not touch it.
        assertEquals(OrderStatus.PENDING_PAYMENT, orders.findById(order.getId()).orElseThrow().getStatus());
        assertEquals(ShippingMethod.LOCAL_PICKUP, shipments.findByOrderId(order.getId()).orElseThrow().getMethod());
    }

    @Test
    void paymentsOfAnotherCustomerCannotBeReadSimulatedOrStarted() throws Exception {
        denied(as(stranger, get("/api/v1/payments/" + paymentId)));
        denied(as(stranger, post("/api/v1/payments/" + paymentId + "/simulate-success")));
        denied(as(stranger, post("/api/v1/payments").header("Idempotency-Key", key())
                .contentType(MediaType.APPLICATION_JSON).content("{\"orderId\":" + order.getId() + "}")));
        assertEquals(PaymentStatus.PENDING, payments.findById(paymentId).orElseThrow().getStatus());
        assertEquals(1, payments.findAll().stream().filter(p -> p.getOrder().getId().equals(order.getId())).count());
    }

    @Test
    void addressBookWishlistAndReviewsAreScopedToTheirOwner() throws Exception {
        denied(as(stranger, put("/api/v1/users/me/addresses/" + addressId).contentType(MediaType.APPLICATION_JSON)
                .content(address("Intruder"))));
        denied(as(stranger, post("/api/v1/users/me/addresses/" + addressId + "/default")));
        denied(as(stranger, delete("/api/v1/users/me/addresses/" + addressId)));
        as(owner, get("/api/v1/users/me/addresses")).andExpect(jsonPath("$[0].fullName").value("Owner Person"));

        // Wishlist paths carry only a product id: the stranger's call touches the stranger's own list.
        as(stranger, delete("/api/v1/users/me/wishlist/" + product.getId())).andExpect(status().is(anyOf(204, 404)));
        as(owner, get("/api/v1/users/me/wishlist")).andExpect(jsonPath("$.content[0].productId").value(product.getId()));

        denied(as(stranger, put("/api/v1/reviews/" + reviewId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"rating\":1,\"body\":\"defaced\"}")));
        denied(as(stranger, delete("/api/v1/reviews/" + reviewId)));
        Review review = reviews.findById(reviewId).orElseThrow();
        assertEquals(ReviewStatus.APPROVED, review.getStatus());
        assertEquals("Owner review body", review.getBody());

        as(stranger, get("/api/v1/users/me/export")).andExpect(status().isOk())
                .andExpect(content().string(not(containsString(owner.getEmail()))))
                .andExpect(content().string(not(containsString(order.getOrderNumber()))));
    }

    private void denied(ResultActions result) throws Exception {
        result.andExpect(status().is(anyOf(403, 404)))
                .andExpect(jsonPath("$.code").exists())
                .andExpect(jsonPath("$.requestId").exists())
                .andExpect(content().string(not(containsString(owner.getEmail()))))
                .andExpect(content().string(not(containsString(order.getOrderNumber()))))
                .andExpect(content().string(not(containsString("Owner Person"))));
    }

    private static org.hamcrest.Matcher<Integer> anyOf(int a, int b) {
        return org.hamcrest.Matchers.anyOf(org.hamcrest.Matchers.is(a), org.hamcrest.Matchers.is(b));
    }

    private ResultActions as(User user, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(customer(user)));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private static String address(String fullName) {
        return "{\"fullName\":\"" + fullName + "\",\"line1\":\"Demo street 1\",\"city\":\"Berlin\",\"state\":\"Berlin\","
                + "\"postalCode\":\"10115\",\"country\":\"Germany\"}";
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor customer(User u) {
        return user(u.getEmail()).roles("CUSTOMER");
    }

    private User saveUser(String prefix) {
        return users.save(User.builder().email(prefix + "-" + key() + "@example.com").passwordHash("test-hash").enabled(true).build());
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

}
