package com.example.shopupu.inventory.service;

import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.catalog.repository.ProductVariantRepository;
import com.example.shopupu.common.exception.OutOfStockException;
import com.example.shopupu.common.exception.ResourceNotFoundException;
import com.example.shopupu.inventory.entity.Inventory;
import com.example.shopupu.inventory.entity.InventoryMovement;
import com.example.shopupu.inventory.repository.InventoryMovementRepository;
import com.example.shopupu.inventory.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryMovementRepository movementRepository;
    private final ProductVariantRepository variantRepository;

    @Transactional(readOnly = true)
    public int availableFor(Long variantId) {
        return inventoryRepository.findByVariant_Id(variantId)
                .map(Inventory::getAvailable)
                .orElse(0);
    }

    /** Batch availability lookup: variant id -> stock - reserved (missing rows -> 0). */
    @Transactional(readOnly = true)
    public java.util.Map<Long, Integer> availabilityFor(java.util.Collection<Long> variantIds) {
        if (variantIds.isEmpty()) {
            return java.util.Map.of();
        }
        java.util.Map<Long, Integer> result = new java.util.HashMap<>();
        for (Inventory inventory : inventoryRepository.findByVariant_IdIn(variantIds)) {
            result.put(inventory.getVariant().getId(), inventory.getAvailable());
        }
        return result;
    }

    /** Reserves stock for an order; atomic, throws {@link OutOfStockException} when short. */
    @Transactional
    public void reserve(Long variantId, int quantity, String reference) {
        requirePositive(quantity);
        int updated = inventoryRepository.reserve(variantId, quantity);
        if (updated == 0) {
            throw new OutOfStockException("Not enough stock for variant: " + variantId);
        }
        record(variantId, InventoryMovement.Type.RESERVE, quantity, reference);
    }

    /** Releases a reservation (order cancelled / payment expired). */
    @Transactional
    public void release(Long variantId, int quantity, String reference) {
        requirePositive(quantity);
        int updated = inventoryRepository.release(variantId, quantity);
        if (updated == 0) {
            throw new IllegalStateException("Cannot release more than reserved for variant: " + variantId);
        }
        record(variantId, InventoryMovement.Type.RELEASE, quantity, reference);
    }

    /** Turns a reservation into an actual sale after successful payment. */
    @Transactional
    public void commitSale(Long variantId, int quantity, String reference) {
        requirePositive(quantity);
        int updated = inventoryRepository.commitSale(variantId, quantity);
        if (updated == 0) {
            throw new IllegalStateException("Cannot commit sale without matching reservation for variant: " + variantId);
        }
        record(variantId, InventoryMovement.Type.SALE, quantity, reference);
    }

    /** Returns goods to stock after a refund. */
    @Transactional
    public void restock(Long variantId, int quantity, String reference) {
        requirePositive(quantity);
        inventoryRepository.restock(variantId, quantity);
        record(variantId, InventoryMovement.Type.RETURN, quantity, reference);
    }

    /** Admin adjustment: sets the absolute stock level of a variant. */
    @Transactional
    public Inventory setStock(Long variantId, int newStock, String reference) {
        if (newStock < 0) {
            throw new IllegalArgumentException("Stock cannot be negative");
        }
        Inventory inventory = inventoryRepository.findByVariant_Id(variantId)
                .orElseGet(() -> createFor(variantId));
        if (newStock < inventory.getReserved()) {
            throw new IllegalStateException("Cannot set stock below currently reserved quantity");
        }
        int delta = newStock - inventory.getStock();
        inventory.setStock(newStock);
        Inventory saved = inventoryRepository.save(inventory);
        if (delta != 0) {
            record(variantId,
                    delta > 0 ? InventoryMovement.Type.RECEIPT : InventoryMovement.Type.ADJUSTMENT,
                    Math.abs(delta), reference);
        }
        return saved;
    }

    private Inventory createFor(Long variantId) {
        ProductVariant variant = variantRepository.findById(variantId)
                .orElseThrow(() -> new ResourceNotFoundException("Variant not found: " + variantId));
        return inventoryRepository.save(Inventory.builder().variant(variant).build());
    }

    private void record(Long variantId, InventoryMovement.Type type, int quantity, String reference) {
        ProductVariant variantRef = variantRepository.getReferenceById(variantId);
        movementRepository.save(InventoryMovement.builder()
                .variant(variantRef)
                .movementType(type)
                .quantity(quantity)
                .reference(reference)
                .build());
    }

    private void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
    }
}
