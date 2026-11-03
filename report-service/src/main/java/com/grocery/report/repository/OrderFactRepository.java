package com.grocery.report.repository;

import com.grocery.report.entity.OrderFact;
import com.grocery.report.enums.ReportOrderStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public interface OrderFactRepository extends JpaRepository<OrderFact, Long> {

    long countByPlacedDate(LocalDate placedDate);

    /** Orders that ended without delivery although the customer had paid online: refund not done yet. */
    @Query("SELECT COUNT(o) FROM OrderFact o WHERE o.cancelled = true AND o.paymentStatus = com.grocery.report.enums.ReportPaymentStatus.PAID")
    long countPendingRefunds();

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM OrderFact o WHERE o.sale = true AND o.placedDate = :date")
    BigDecimal salesPlacedOn(LocalDate date);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM OrderFact o WHERE o.status = :status AND o.deliveredDate = :date")
    BigDecimal totalOfStatusDeliveredOn(ReportOrderStatus status, LocalDate date);

    @Query("SELECT COUNT(DISTINCT o.customerUserId) FROM OrderFact o WHERE o.placedAt >= :since")
    long countCustomersWhoOrderedSince(Instant since);

    /** MySQL's TIMESTAMPDIFF is the simplest way to subtract two timestamps, so this one is native SQL. */
    @Query(value = "SELECT AVG(TIMESTAMPDIFF(MINUTE, placed_at, delivered_at)) FROM order_facts WHERE delivered_at >= :since",
           nativeQuery = true)
    Double averageDeliveryMinutesSince(Instant since);

    /** Per day: number of orders, and how many ended without delivery. */
    @Query("""
            SELECT new com.grocery.report.repository.DailyOrderCountRow(o.placedDate, COUNT(o),
                   SUM(CASE WHEN o.cancelled = true THEN 1 ELSE 0 END))
            FROM OrderFact o
            WHERE o.placedDate BETWEEN :from AND :to AND (:storeId IS NULL OR o.storeId = :storeId)
            GROUP BY o.placedDate
            """)
    List<DailyOrderCountRow> dailyOrderCounts(LocalDate from, LocalDate to, Long storeId);

    /** Per day: the money of the orders that count as sales. */
    @Query("""
            SELECT new com.grocery.report.repository.DailySalesRow(o.placedDate, COUNT(o), SUM(o.itemsTotal),
                   SUM(o.couponDiscount), SUM(o.deliveryFee), SUM(o.totalAmount))
            FROM OrderFact o
            WHERE o.sale = true AND o.placedDate BETWEEN :from AND :to AND (:storeId IS NULL OR o.storeId = :storeId)
            GROUP BY o.placedDate
            """)
    List<DailySalesRow> dailySales(LocalDate from, LocalDate to, Long storeId);

    /** The stores with the highest sales (limit through the Pageable). */
    @Query("""
            SELECT new com.grocery.report.repository.StoreSalesRow(o.storeId, COUNT(o), SUM(o.totalAmount))
            FROM OrderFact o
            WHERE o.sale = true AND o.placedDate BETWEEN :from AND :to
            GROUP BY o.storeId
            ORDER BY SUM(o.totalAmount) DESC
            """)
    List<StoreSalesRow> topStores(LocalDate from, LocalDate to, Pageable limit);

    /** Delivered orders of the period (commission report). */
    List<OrderFact> findByStatusAndDeliveredDateBetweenOrderByStoreIdAsc(ReportOrderStatus status, LocalDate from, LocalDate to);
}
