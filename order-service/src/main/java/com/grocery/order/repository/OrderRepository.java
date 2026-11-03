package com.grocery.order.repository;

import com.grocery.order.entity.Order;
import com.grocery.order.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    /** Customer access: only their own orders. */
    Optional<Order> findByIdAndCustomerUserId(Long id, Long customerUserId);

    /** Vendor access: only their store's orders. */
    Optional<Order> findByIdAndStoreId(Long id, Long storeId);

    /** A repeated order request: the order the customer placed with this Idempotency-Key. */
    Optional<Order> findByCustomerUserIdAndIdempotencyKey(Long customerUserId, String idempotencyKey);

    /** The customer's orders of exactly this cart (its checkout fingerprint) since a moment ago, still going, newest first. */
    @Query("""
            SELECT o FROM Order o
            WHERE o.customerUserId = :customerUserId
              AND o.checkoutFingerprint = :checkoutFingerprint
              AND o.createdAt > :since
              AND o.status NOT IN (com.grocery.order.enums.OrderStatus.CANCELLED, com.grocery.order.enums.OrderStatus.REJECTED)
            ORDER BY o.id DESC
            """)
    List<Order> findRecentOrdersOfCart(Long customerUserId, String checkoutFingerprint, Instant since);

    /**
     * One search for every panel: every filter is optional (null = ignore it).
     * Customers pass their own user id, vendors their own store id.
     */
    @Query("""
            SELECT o FROM Order o
            WHERE (:customerUserId IS NULL OR o.customerUserId = :customerUserId)
              AND (:storeId IS NULL OR o.storeId = :storeId)
              AND (:status IS NULL OR o.status = :status)
            """)
    Page<Order> search(Long customerUserId, Long storeId, OrderStatus status, Pageable pageable);

    /**
     * The order, locked until the transaction ends (SELECT ... FOR UPDATE): a second request for the same order waits
     * here. Used so that an invoice is generated only once (InvoiceServiceImpl).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :orderId")
    Optional<Order> findByIdForUpdate(Long orderId);
}
