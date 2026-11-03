package com.grocery.cart.entity;

import com.grocery.cart.enums.DiscountType;
import com.grocery.cart.exception.BusinessException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * A discount code (DDD aggregate root).
 *
 *   storeId == null  -> PLATFORM coupon, created by an admin, valid in every store
 *   storeId != null  -> STORE coupon, created by that store's vendor, valid only in that store
 *
 * checkCanBeUsed(...) holds every rule for using a coupon; discountFor(...) calculates the discount.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "coupons")
public class Coupon extends BaseEntity {

    /** Stored in upper case; customers may type it in any case. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "store_id")
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "discount_type", nullable = false, length = 20)
    private DiscountType discountType;

    /** Percent (PERCENTAGE) or amount (FLAT). */
    @Column(name = "discount_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal discountValue;

    /** Upper limit for a PERCENTAGE discount; null = no limit. */
    @Column(name = "max_discount_amount", precision = 10, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "min_order_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal minOrderAmount;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    /** How often the coupon may be used in total; null = unlimited. */
    @Column(name = "usage_limit")
    private Integer usageLimit;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    /** How often one customer may use it. */
    @Column(name = "per_customer_limit", nullable = false)
    private int perCustomerLimit;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by_user_id", nullable = false, updatable = false)
    private Long createdByUserId;

    public boolean isPlatformCoupon() {
        return storeId == null;
    }

    /** Checks the values a vendor or admin entered (called on create and update). */
    public void checkSettings() {
        if (discountType == DiscountType.PERCENTAGE && discountValue.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new BusinessException("A percentage discount cannot be more than 100");
        }
        if (discountType == DiscountType.FLAT && maxDiscountAmount != null) {
            throw new BusinessException("maxDiscountAmount is only used for PERCENTAGE coupons");
        }
        if (!validUntil.isAfter(validFrom)) {
            throw new BusinessException("validUntil must be after validFrom");
        }
    }

    /**
     * Can this coupon be used on a cart? Throws BusinessException with the reason when not.
     *
     * @param cartStoreId          store of the items in the cart
     * @param itemsTotal           what the items cost (after product discounts)
     * @param now                  current time
     * @param timesUsedByCustomer  how often this customer already used the coupon
     */
    public void checkCanBeUsed(Long cartStoreId, BigDecimal itemsTotal, Instant now, long timesUsedByCustomer) {
        if (!active) {
            throw new BusinessException("Coupon " + code + " is not active");
        }
        if (now.isBefore(validFrom)) {
            throw new BusinessException("Coupon " + code + " is not valid yet");
        }
        if (now.isAfter(validUntil)) {
            throw new BusinessException("Coupon " + code + " has expired");
        }
        if (!isPlatformCoupon() && !storeId.equals(cartStoreId)) {
            throw new BusinessException("Coupon " + code + " is only valid in another store");
        }
        if (itemsTotal.compareTo(minOrderAmount) < 0) {
            throw new BusinessException("Coupon %s needs an items total of at least %s (add %s more)"
                    .formatted(code, minOrderAmount, minOrderAmount.subtract(itemsTotal)));
        }
        if (usageLimit != null && usedCount >= usageLimit) {
            throw new BusinessException("Coupon " + code + " has been fully used");
        }
        if (timesUsedByCustomer >= perCustomerLimit) {
            throw new BusinessException("You have already used coupon " + code
                    + (perCustomerLimit == 1 ? "" : " " + perCustomerLimit + " times"));
        }
    }

    /** The discount on the given items total. Never more than the items total itself. */
    public BigDecimal discountFor(BigDecimal itemsTotal) {
        BigDecimal discount;
        if (discountType == DiscountType.PERCENTAGE) {
            discount = itemsTotal.multiply(discountValue).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (maxDiscountAmount != null && discount.compareTo(maxDiscountAmount) > 0) {
                discount = maxDiscountAmount;
            }
        } else {
            discount = discountValue;
        }
        return discount.min(itemsTotal).setScale(2, RoundingMode.HALF_UP);
    }

    /** An order used this coupon (OrderCreated). */
    public void recordUse() {
        this.usedCount++;
    }

    /** That order was cancelled: the use no longer counts (OrderCancelled). */
    public void releaseUse() {
        this.usedCount = Math.max(0, usedCount - 1);
    }
}
