package com.example.shopupu.orders.repository;

import com.example.shopupu.orders.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;


/**
 * describes the OrderItemRepository interface.
 */
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}