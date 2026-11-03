package com.grocery.cart.repository;

import com.grocery.cart.entity.Coupon;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CouponRepository extends JpaRepository<Coupon, Long> {

    Optional<Coupon> findByCode(String code);

    boolean existsByCode(String code);

    /** Vendor access: finds the coupon only if it belongs to the vendor's store. */
    Optional<Coupon> findByIdAndStoreId(Long id, Long storeId);

    Page<Coupon> findByStoreId(Long storeId, Pageable pageable);

    /**
     * Admin search. storeId = null: every coupon; platformOnly = true: only coupons without a store.
     */
    @Query("""
            SELECT c FROM Coupon c
            WHERE (:storeId IS NULL OR c.storeId = :storeId)
              AND (:platformOnly = false OR c.storeId IS NULL)
            """)
    Page<Coupon> search(Long storeId, boolean platformOnly, Pageable pageable);

    /**
     * Coupons a customer can use right now: active, inside their validity period, and either
     * platform-wide or belonging to the given store (storeId = null: platform coupons only).
     */
    @Query("""
            SELECT c FROM Coupon c
            WHERE c.active = true
              AND c.validFrom <= :now AND c.validUntil >= :now
              AND (c.storeId IS NULL OR c.storeId = :storeId)
            """)
    List<Coupon> findAvailable(Long storeId, Instant now, Sort sort);
}
