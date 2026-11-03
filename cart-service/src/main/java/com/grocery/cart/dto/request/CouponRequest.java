package com.grocery.cart.dto.request;

import com.grocery.cart.constant.ValidationPatterns;
import com.grocery.cart.enums.DiscountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Create or update a coupon (vendor: for their store; admin: platform-wide).
 *
 * @param discountValue     percent for PERCENTAGE (e.g. 10), amount for FLAT (e.g. 50.00)
 * @param maxDiscountAmount optional upper limit for a PERCENTAGE discount
 * @param minOrderAmount    the items total the cart needs (0 = no minimum)
 * @param usageLimit        optional: how often the coupon may be used in total
 * @param perCustomerLimit  optional: how often one customer may use it (default 1)
 */
public record CouponRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.COUPON_CODE, message = "must be 4-20 letters or digits") String code,
        @NotBlank @Size(max = 255) String description,
        @NotNull DiscountType discountType,
        @NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal discountValue,
        @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal maxDiscountAmount,
        @NotNull @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal minOrderAmount,
        @NotNull Instant validFrom,
        @NotNull Instant validUntil,
        @Min(1) Integer usageLimit,
        @Min(1) @Max(100) Integer perCustomerLimit) {

    public int perCustomerLimitOrDefault() {
        return perCustomerLimit == null ? 1 : perCustomerLimit;
    }
}
