package com.grocery.report.repository;

import com.grocery.report.entity.OrderItemFact;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;

public interface OrderItemFactRepository extends JpaRepository<OrderItemFact, Long> {

    /** The best-selling products (by quantity) of the orders that count as sales. */
    @Query("""
            SELECT new com.grocery.report.repository.ProductSalesRow(i.productId, i.productName, SUM(i.quantity), SUM(i.lineTotal))
            FROM OrderItemFact i JOIN OrderFact o ON o.orderId = i.orderId
            WHERE o.sale = true AND o.placedDate BETWEEN :from AND :to AND (:storeId IS NULL OR o.storeId = :storeId)
            GROUP BY i.productId, i.productName
            ORDER BY SUM(i.quantity) DESC
            """)
    List<ProductSalesRow> topProducts(LocalDate from, LocalDate to, Long storeId, Pageable limit);
}
