package com.grocery.delivery.repository;

import com.grocery.delivery.entity.DeliveryPartner;
import com.grocery.delivery.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface DeliveryPartnerRepository extends JpaRepository<DeliveryPartner, Long> {

    Optional<DeliveryPartner> findByUserId(Long userId);

    boolean existsByUserId(Long userId);

    /** Approved partners of a zone, oldest first (the order they are offered deliveries in). */
    List<DeliveryPartner> findByZoneIdAndKycStatusOrderByIdAsc(Long zoneId, KycStatus kycStatus);

    /** Admin search: kycStatus = null means all partners. */
    @Query("SELECT p FROM DeliveryPartner p WHERE (:kycStatus IS NULL OR p.kycStatus = :kycStatus)")
    Page<DeliveryPartner> search(KycStatus kycStatus, Pageable pageable);
}
