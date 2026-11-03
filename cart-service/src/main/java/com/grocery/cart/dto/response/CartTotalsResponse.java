package com.grocery.cart.dto.response;

import java.math.BigDecimal;

/**
 * The money part of a cart. All prices include tax.
 *
 *   payableAmount = itemsTotal - couponDiscount + deliveryFee
 *
 * @param itemsTotal        sum of the line totals (after product discounts)
 * @param productSavings    what the product discounts save compared to the MRP
 * @param couponDiscount    0 when there is no usable coupon
 * @param deliveryFee       0 when itemsTotal reaches the free-delivery amount
 * @param taxIncluded       the tax (GST) contained in itemsTotal, for information
 * @param minimumOrderMet   false = the customer cannot check out yet
 */
public record CartTotalsResponse(
        BigDecimal itemsTotal,
        BigDecimal productSavings,
        BigDecimal couponDiscount,
        BigDecimal deliveryFee,
        BigDecimal payableAmount,
        BigDecimal taxIncluded,
        BigDecimal minimumOrderValue,
        boolean minimumOrderMet) {
}
