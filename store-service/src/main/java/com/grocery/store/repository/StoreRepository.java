package com.grocery.store.repository;

import com.grocery.store.entity.Store;
import com.grocery.store.enums.StoreStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByOwnerUserId(Long ownerUserId);

    boolean existsByOwnerUserId(Long ownerUserId);

    Optional<Store> findByIdAndStatus(Long id, StoreStatus status);

    /**
     * Admin store search. Every filter is optional: pass null to ignore it.
     */
    @Query("""
            SELECT s FROM Store s
            WHERE (:status IS NULL OR s.status = :status)
              AND (:city IS NULL OR LOWER(s.city) = LOWER(:city))
              AND (:keyword IS NULL OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')))
            """)
    Page<Store> search(StoreStatus status, String city, String keyword, Pageable pageable);

    /**
     * ACTIVE stores for customers, optionally only in one city and/or only stores delivering to a PIN code.
     */
    @Query("""
            SELECT s FROM Store s
            WHERE s.status = com.grocery.store.enums.StoreStatus.ACTIVE
              AND (:city IS NULL OR LOWER(s.city) = LOWER(:city))
              AND (:pincode IS NULL OR :pincode MEMBER OF s.deliveryPincodes)
            """)
    Page<Store> findActive(String city, String pincode, Pageable pageable);
}
