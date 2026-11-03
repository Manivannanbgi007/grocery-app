package com.grocery.delivery.repository;

import com.grocery.delivery.entity.DeliveryZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DeliveryZoneRepository extends JpaRepository<DeliveryZone, Long> {

    /** Active zones that contain the PIN code (normally one). */
    @Query("SELECT z FROM DeliveryZone z WHERE z.active = true AND :pincode MEMBER OF z.pincodes ORDER BY z.id")
    List<DeliveryZone> findActiveCovering(String pincode);

    boolean existsByName(String name);
}
