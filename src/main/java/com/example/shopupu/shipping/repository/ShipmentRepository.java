package com.example.shopupu.shipping.repository;

import com.example.shopupu.orders.entity.Order;
import com.example.shopupu.shipping.entity.Shipment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * describes the ShipmentRepository interface.
 */
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByOrder(Order order);
    Optional<Shipment> findByOrderId(Long orderId);
}
