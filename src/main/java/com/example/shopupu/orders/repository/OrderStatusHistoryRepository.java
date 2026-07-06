package com.example.shopupu.orders.repository;

import com.example.shopupu.orders.entity.OrderStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
    List<OrderStatusHistory> findByOrder_IdOrderByCreatedAtAsc(Long orderId);
}
