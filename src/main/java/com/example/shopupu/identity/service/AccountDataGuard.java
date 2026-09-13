package com.example.shopupu.identity.service;

import com.example.shopupu.common.exception.ForbiddenOperationException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Serializes personal-data writes with erasure, including requests admitted before deletion. */
@Component
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AccountDataGuard {
    private final JdbcClient jdbc;

    public void lockActive(long userId) {
        // A scalar SQL result cannot reuse a stale managed User loaded before the lock.
        boolean active = jdbc.sql("select enabled and deleted_at is null from users where id = :id for update")
                .param("id", userId).query(Boolean.class).optional()
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        if (!active) {
            throw new ForbiddenOperationException("Account is unavailable");
        }
    }

    public void lockOrderOwner(long orderId) {
        Long userId = jdbc.sql("select user_id from orders where id = :id").param("id", orderId)
                .query(Long.class).optional().orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        lockActive(userId);
    }

    public void lockReviewOwner(long reviewId) {
        Long userId = jdbc.sql("select user_id from reviews where id = :id").param("id", reviewId)
                .query(Long.class).optional().orElseThrow(() -> new ResourceNotFoundException("Review not found"));
        lockActive(userId);
    }
}
