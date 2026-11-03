package com.grocery.cart.dto.request;

import com.grocery.cart.constant.ValidationPatterns;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApplyCouponRequest(
        @NotBlank @Pattern(regexp = ValidationPatterns.COUPON_CODE, message = "must be 4-20 letters or digits") String code) {
}
