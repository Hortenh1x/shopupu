package com.example.shopupu.orders.service;

import com.example.shopupu.orders.repository.OrderRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Human-readable order numbers like ORD-20260705-4F7K2Q (ORD-08). */
@Component
@RequiredArgsConstructor
public class OrderNumberGenerator {

    private static final char[] ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final DateTimeFormatter DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final OrderRepository orderRepository;

    public String next() {
        for (int attempt = 0; attempt < 10; attempt++) {
            String candidate = "ORD-" + LocalDate.now(ZoneOffset.UTC).format(DATE) + "-" + randomSuffix();
            if (!orderRepository.existsByOrderNumber(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate a unique order number");
    }

    private String randomSuffix() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return sb.toString();
    }
}
