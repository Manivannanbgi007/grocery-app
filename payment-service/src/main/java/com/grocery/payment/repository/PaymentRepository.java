package com.grocery.payment.repository;

import com.grocery.payment.entity.Payment;
import com.grocery.payment.enums.PaymentMethod;
import com.grocery.payment.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByOrderId(Long orderId);

    boolean existsByOrderId(Long orderId);

    /** Customer access: only the payments of their own orders. */
    Optional<Payment> findByOrderIdAndCustomerUserId(Long orderId, Long customerUserId);

    /** Webhooks name the gateway's order (Razorpay order_...), not ours. */
    Optional<Payment> findByGatewayOrderId(String gatewayOrderId);

    /** Online payments whose deadline has passed (PaymentTimeoutJob). */
    @Query("SELECT p.id FROM Payment p WHERE p.status = :status AND p.payBefore < :now ORDER BY p.id")
    List<Long> findIdsWithDeadlineBefore(PaymentStatus status, Instant now);

    /** Admin summary: number and sum of payments per method and status, created in [from, to). */
    @Query("""
            SELECT new com.grocery.payment.repository.PaymentTotals(p.method, p.status, COUNT(p), SUM(p.amount))
            FROM Payment p
            WHERE p.createdAt >= :from AND p.createdAt < :to
            GROUP BY p.method, p.status
            ORDER BY p.method, p.status
            """)
    List<PaymentTotals> totalsCreatedBetween(Instant from, Instant to);

    /** Admin search: every filter is optional (null = ignore it). */
    @Query("""
            SELECT p FROM Payment p
            WHERE (:status IS NULL OR p.status = :status)
              AND (:method IS NULL OR p.method = :method)
              AND (:storeId IS NULL OR p.storeId = :storeId)
            """)
    Page<Payment> search(PaymentStatus status, PaymentMethod method, Long storeId, Pageable pageable);
}
