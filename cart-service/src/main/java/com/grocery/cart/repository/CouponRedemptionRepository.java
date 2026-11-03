package com.grocery.cart.repository;

import com.grocery.cart.entity.CouponRedemption;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CouponRedemptionRepository extends JpaRepository<CouponRedemption, Long> {

    long countByCouponIdAndCustomerUserId(Long couponId, Long customerUserId);

    boolean existsByOrderId(Long orderId);

    Optional<CouponRedemption> findByOrderId(Long orderId);
}
