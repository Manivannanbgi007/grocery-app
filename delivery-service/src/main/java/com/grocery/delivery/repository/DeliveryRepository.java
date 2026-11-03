package com.grocery.delivery.repository;

import com.grocery.delivery.entity.Delivery;
import com.grocery.delivery.enums.DeliveryStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {

    Optional<Delivery> findByOrderId(Long orderId);

    /** Customer access: only the delivery of their own order. */
    Optional<Delivery> findByOrderIdAndCustomerUserId(Long orderId, Long customerUserId);

    /** Partner access: only deliveries assigned to them. */
    Optional<Delivery> findByIdAndPartnerUserId(Long id, Long partnerUserId);

    boolean existsByPartnerUserIdAndStatusIn(Long partnerUserId, Collection<DeliveryStatus> statuses);

    /** Deliveries of a zone waiting for a partner, oldest first. */
    List<Delivery> findByZoneIdAndStatusOrderByIdAsc(Long zoneId, DeliveryStatus status);

    Page<Delivery> findByPartnerUserIdAndStatusIn(Long partnerUserId, Collection<DeliveryStatus> statuses, Pageable pageable);

    /** Completed deliveries of a partner in a time window (earnings). */
    List<Delivery> findByPartnerUserIdAndStatusAndDeliveredAtBetween(Long partnerUserId, DeliveryStatus status,
                                                                     Instant from, Instant to);

    /** Admin search: every filter is optional (null = ignore it). */
    @Query("""
            SELECT d FROM Delivery d
            WHERE (:status IS NULL OR d.status = :status)
              AND (:partnerUserId IS NULL OR d.partnerUserId = :partnerUserId)
            """)
    Page<Delivery> search(DeliveryStatus status, Long partnerUserId, Pageable pageable);
}
