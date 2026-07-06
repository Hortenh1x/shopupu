package com.example.shopupu.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.orders.entity.OrderStatus;
import com.example.shopupu.orders.repository.OrderRepository;
import com.example.shopupu.support.PostgresContainerSupport;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * TEST-03/AUTHZ: deny-by-default, role separation and IDOR protection
 * verified against the real security chain and database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class SecurityAccessIT extends PostgresContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrderRepository orderRepository;

    private User owner;
    private User stranger;
    private Order order;

    @BeforeEach
    void setUp() {
        owner = saveUser("owner");
        stranger = saveUser("stranger");
        order = orderRepository.save(Order.builder()
                .orderNumber("ORD-SEC-" + System.nanoTime())
                .user(owner)
                .status(OrderStatus.CREATED)
                .subtotalAmount(new BigDecimal("10.00"))
                .paymentAmount(new BigDecimal("10.00"))
                .build());
    }

    @Test
    void publicCatalogIsOpen() throws Exception {
        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(status().isOk());
    }

    @Test
    void unknownEndpointRequiresAuthentication() throws Exception {
        // deny-by-default: anything not whitelisted must not be public
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/cart"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void actuatorIsRestricted() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/actuator/metrics")
                        .with(customer(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpointsRejectCustomers() throws Exception {
        mockMvc.perform(get("/api/admin/orders").with(customer(stranger)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/users").with(customer(stranger)))
                .andExpect(status().isForbidden());
    }

    @Test
    void managerCannotManageUsers() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .with(user(stranger.getEmail()).roles("MANAGER")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders")
                        .with(user(stranger.getEmail()).roles("MANAGER")))
                .andExpect(status().isOk());
    }

    @Test
    void foreignOrderIsNotAccessible() throws Exception {
        mockMvc.perform(get("/api/orders/" + order.getId()).with(customer(owner)))
                .andExpect(status().isOk());
        // IDOR (AUTHZ-04): another customer must not read someone else's order
        mockMvc.perform(get("/api/orders/" + order.getId()).with(customer(stranger)))
                .andExpect(status().isForbidden());
    }

    private SecurityMockMvcRequestPostProcessors.UserRequestPostProcessor customer(User u) {
        return user(u.getEmail()).roles("CUSTOMER");
    }

    private User saveUser(String prefix) {
        return userRepository.save(User.builder()
                .email(prefix + "-" + System.nanoTime() + "@example.com")
                .passwordHash("test-hash")
                .enabled(true)
                .build());
    }
}
