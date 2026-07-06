package com.example.shopupu.security;

import com.example.shopupu.config.RateLimitProperties;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-client-IP token buckets for brute-force-sensitive endpoints (SEC-05, AUTH-08).
 * In-memory buckets: sufficient for a single instance; swap the cache for a
 * distributed Bucket4j backend (Redis) when scaling horizontally.
 */
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitProperties properties;

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterAccess(Duration.ofMinutes(15))
            .build();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (!properties.isEnabled()) {
            chain.doFilter(request, response);
            return;
        }

        Zone zone = zoneFor(request);
        if (zone == Zone.NONE) {
            chain.doFilter(request, response);
            return;
        }

        String key = zone + ":" + clientIp(request);
        Bucket bucket = buckets.get(key, k -> newBucket(zone));

        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
        if (probe.isConsumed()) {
            response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            chain.doFilter(request, response);
            return;
        }

        long retryAfterSec = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000L);
        response.setStatus(429);
        response.setHeader("Retry-After", String.valueOf(retryAfterSec));
        response.setHeader("X-RateLimit-Remaining", "0");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("""
                {"type":"urn:shopupu:error:rate-limit","title":"Too Many Requests","status":429,\
                "detail":"Rate limit exceeded, retry later"}""");
    }

    private enum Zone { AUTH, CHECKOUT, NONE }

    private Zone zoneFor(HttpServletRequest request) {
        if (!"POST".equals(request.getMethod())) {
            return Zone.NONE;
        }
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/auth/")) {
            return Zone.AUTH;
        }
        if (uri.equals("/api/orders/checkout") || uri.equals("/api/payments")
                || uri.equals("/api/payments/create") || uri.equals("/api/payments/callback")) {
            return Zone.CHECKOUT;
        }
        return Zone.NONE;
    }

    private Bucket newBucket(Zone zone) {
        long capacity = zone == Zone.AUTH ? properties.getAuthCapacity() : properties.getCheckoutCapacity();
        long refill = zone == Zone.AUTH ? properties.getAuthRefillPerMinute() : properties.getCheckoutRefillPerMinute();
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(refill, Duration.ofMinutes(1))
                        .build())
                .build();
    }

    private String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
