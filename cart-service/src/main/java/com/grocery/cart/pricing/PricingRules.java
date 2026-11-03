package com.grocery.cart.pricing;

import java.math.BigDecimal;

/**
 * The numbers the cart total depends on (set by the super admin in platform-service).
 */
public record PricingRules(BigDecimal deliveryFee, BigDecimal freeDeliveryFrom, BigDecimal minimumOrderValue) {
}
