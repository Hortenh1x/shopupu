package com.example.shopupu.payments.service;

import com.example.shopupu.config.CheckoutProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Times out payments the provider never confirmed (PAY-04). */
@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentExpirationJob {

    private final PaymentService paymentService;
    private final CheckoutProperties checkoutProperties;

    @Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT2M")
    public void expireStalePayments() {
        Instant cutoff = Instant.now().minus(checkoutProperties.getPendingPaymentTtlMin(), ChronoUnit.MINUTES);
        int expired = paymentService.expireStalePayments(cutoff);
        if (expired > 0) {
            log.info("Expired {} stale payments past the confirmation TTL", expired);
        }
    }
}
