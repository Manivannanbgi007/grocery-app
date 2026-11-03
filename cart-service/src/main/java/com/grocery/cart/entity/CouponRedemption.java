package com.grocery.cart.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * One use of a coupon by an order. Used to enforce the per-customer limit.
 * order_id is unique, so the same order can never count twice.
 * Rows are written when order-service publishes OrderCreated (Phase 7).
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "coupon_redemptions")
public class CouponRedemption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "coupon_id", nullable = false)
    private Long couponId;

    @Column(name = "customer_user_id", nullable = false)
    private Long customerUserId;

    @Column(name = "order_id", nullable = false, unique = true)
    private Long orderId;

    @Column(name = "discount_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountAmount;

    @Column(name = "redeemed_at", nullable = false)
    private Instant redeemedAt;
}
