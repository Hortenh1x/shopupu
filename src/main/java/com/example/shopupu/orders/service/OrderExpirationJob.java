package com.example.shopupu.orders.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Releases inventory held by unpaid orders after the checkout TTL (INV-02). */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationJob {

    private final OrderService orderService;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
    public void expireStaleOrders() {
        int expired = orderService.expireStaleOrders();
        if (expired > 0) {
            log.info("Auto-cancelled {} unpaid orders past the reservation TTL", expired);
        }
    }
}
