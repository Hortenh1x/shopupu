package com.example.shopupu.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.shopupu.catalog.entity.ProductVariant;
import com.example.shopupu.catalog.repository.ProductVariantRepository;
import com.example.shopupu.common.exception.OutOfStockException;
import com.example.shopupu.inventory.entity.Inventory;
import com.example.shopupu.inventory.entity.InventoryMovement;
import com.example.shopupu.inventory.repository.InventoryMovementRepository;
import com.example.shopupu.inventory.repository.InventoryRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private InventoryRepository inventoryRepository;

    @Mock
    private InventoryMovementRepository movementRepository;

    @Mock
    private ProductVariantRepository variantRepository;

    @InjectMocks
    private InventoryService inventoryService;

    @Test
    void reserveRecordsMovementOnSuccess() {
        when(inventoryRepository.reserve(1L, 2)).thenReturn(1);
        when(variantRepository.getReferenceById(1L)).thenReturn(variant(1L));

        inventoryService.reserve(1L, 2, "order:ORD-1");

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        assertEquals(InventoryMovement.Type.RESERVE, captor.getValue().getMovementType());
        assertEquals(2, captor.getValue().getQuantity());
        assertEquals("order:ORD-1", captor.getValue().getReference());
    }

    @Test
    void reserveThrowsOutOfStockWhenNoRowsUpdated() {
        when(inventoryRepository.reserve(1L, 5)).thenReturn(0);

        assertThrows(OutOfStockException.class, () -> inventoryService.reserve(1L, 5, "order:ORD-1"));
        verify(movementRepository, never()).save(any());
    }

    @Test
    void reserveRejectsNonPositiveQuantity() {
        assertThrows(IllegalArgumentException.class, () -> inventoryService.reserve(1L, 0, "x"));
        assertThrows(IllegalArgumentException.class, () -> inventoryService.reserve(1L, -1, "x"));
    }

    @Test
    void releaseThrowsWhenNothingReserved() {
        when(inventoryRepository.release(1L, 2)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> inventoryService.release(1L, 2, "x"));
    }

    @Test
    void commitSaleThrowsWithoutMatchingReservation() {
        when(inventoryRepository.commitSale(1L, 2)).thenReturn(0);

        assertThrows(IllegalStateException.class, () -> inventoryService.commitSale(1L, 2, "x"));
    }

    @Test
    void restockRecordsReturnMovement() {
        when(inventoryRepository.restock(1L, 3)).thenReturn(1);
        when(variantRepository.getReferenceById(1L)).thenReturn(variant(1L));

        inventoryService.restock(1L, 3, "refund:ORD-1");

        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        assertEquals(InventoryMovement.Type.RETURN, captor.getValue().getMovementType());
    }

    @Test
    void setStockRejectsLevelBelowReserved() {
        Inventory inventory = Inventory.builder().variant(variant(1L)).stock(10).reserved(4).build();
        when(inventoryRepository.findByVariant_Id(1L)).thenReturn(Optional.of(inventory));

        assertThrows(IllegalStateException.class, () -> inventoryService.setStock(1L, 3, "admin"));
    }

    @Test
    void setStockCreatesInventoryWhenMissing() {
        ProductVariant variant = variant(1L);
        when(inventoryRepository.findByVariant_Id(1L)).thenReturn(Optional.empty());
        when(variantRepository.findById(1L)).thenReturn(Optional.of(variant));
        when(variantRepository.getReferenceById(1L)).thenReturn(variant);
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Inventory result = inventoryService.setStock(1L, 7, "admin:init");

        assertEquals(7, result.getStock());
        ArgumentCaptor<InventoryMovement> captor = ArgumentCaptor.forClass(InventoryMovement.class);
        verify(movementRepository).save(captor.capture());
        assertEquals(InventoryMovement.Type.RECEIPT, captor.getValue().getMovementType());
        assertEquals(7, captor.getValue().getQuantity());
    }

    @Test
    void availabilityForMapsVariantIds() {
        Inventory a = Inventory.builder().variant(variant(1L)).stock(5).reserved(2).build();
        Inventory b = Inventory.builder().variant(variant(2L)).stock(3).reserved(0).build();
        when(inventoryRepository.findByVariant_IdIn(List.of(1L, 2L))).thenReturn(List.of(a, b));

        Map<Long, Integer> availability = inventoryService.availabilityFor(List.of(1L, 2L));

        assertEquals(3, availability.get(1L));
        assertEquals(3, availability.get(2L));
    }

    private ProductVariant variant(Long id) {
        return ProductVariant.builder().id(id).sku("SKU-" + id).build();
    }
}
