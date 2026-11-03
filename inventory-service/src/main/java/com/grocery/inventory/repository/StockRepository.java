package com.grocery.inventory.repository;

import com.grocery.inventory.entity.Stock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface StockRepository extends JpaRepository<Stock, Long> {

    Optional<Stock> findByProductId(Long productId);

    /** Vendor access: finds the product's stock only if it belongs to the vendor's store. */
    Optional<Stock> findByProductIdAndStoreId(Long productId, Long storeId);

    /** The stock of several products of one store, e.g. all products of an order. */
    List<Stock> findByStoreIdAndProductIdIn(Long storeId, Collection<Long> productIds);

    /**
     * Stock search. storeId is optional (null = every store);
     * lowStockOnly keeps items whose available units are at or below their threshold.
     */
    @Query("""
            SELECT s FROM Stock s
            WHERE (:storeId IS NULL OR s.storeId = :storeId)
              AND (:lowStockOnly = false OR s.quantityOnHand - s.reservedQuantity <= s.lowStockThreshold)
            """)
    Page<Stock> search(Long storeId, boolean lowStockOnly, Pageable pageable);
}
