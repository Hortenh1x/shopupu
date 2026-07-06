package com.example.shopupu.inventory.repository;

import com.example.shopupu.inventory.entity.InventoryMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, Long> {
    Page<InventoryMovement> findByVariant_Id(Long variantId, Pageable pageable);
}
