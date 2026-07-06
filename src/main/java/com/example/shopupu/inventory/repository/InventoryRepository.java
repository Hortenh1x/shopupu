package com.example.shopupu.inventory.repository;

import com.example.shopupu.inventory.entity.Inventory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {

    Optional<Inventory> findByVariant_Id(Long variantId);

    List<Inventory> findByVariant_IdIn(Collection<Long> variantIds);

    /**
     * Atomic reservation (INV-03/DB-06): succeeds only while available stock
     * covers the quantity, so concurrent checkouts can never oversell.
     */
    @Modifying
    @Query("""
            update Inventory i set i.reserved = i.reserved + :qty, i.version = i.version + 1
            where i.variant.id = :variantId and i.stock - i.reserved >= :qty""")
    int reserve(@Param("variantId") Long variantId, @Param("qty") int qty);

    @Modifying
    @Query("""
            update Inventory i set i.reserved = i.reserved - :qty, i.version = i.version + 1
            where i.variant.id = :variantId and i.reserved >= :qty""")
    int release(@Param("variantId") Long variantId, @Param("qty") int qty);

    /** Converts a reservation into a sale: both stock and reserved decrease. */
    @Modifying
    @Query("""
            update Inventory i set i.stock = i.stock - :qty, i.reserved = i.reserved - :qty, i.version = i.version + 1
            where i.variant.id = :variantId and i.stock >= :qty and i.reserved >= :qty""")
    int commitSale(@Param("variantId") Long variantId, @Param("qty") int qty);

    /** Returns goods to stock (refund/restock). */
    @Modifying
    @Query("""
            update Inventory i set i.stock = i.stock + :qty, i.version = i.version + 1
            where i.variant.id = :variantId""")
    int restock(@Param("variantId") Long variantId, @Param("qty") int qty);
}
