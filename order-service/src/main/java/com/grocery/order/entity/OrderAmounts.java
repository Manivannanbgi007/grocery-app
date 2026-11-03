package com.grocery.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * The money of an order, copied from the cart's checkout summary when the order was placed.
 * All prices include tax.  totalAmount = itemsTotal - couponDiscount + deliveryFee
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Embeddable
public class OrderAmounts {

    @Column(name = "items_total", nullable = false, precision = 10, scale = 2)
    private BigDecimal itemsTotal;

    @Column(name = "coupon_code", length = 20)
    private String couponCode;

    @Column(name = "coupon_discount", nullable = false, precision = 10, scale = 2)
    private BigDecimal couponDiscount;

    @Column(name = "delivery_fee", nullable = false, precision = 10, scale = 2)
    private BigDecimal deliveryFee;

    /** The tax (GST) contained in itemsTotal, for the invoice. */
    @Column(name = "tax_included", nullable = false, precision = 10, scale = 2)
    private BigDecimal taxIncluded;

    /** What the customer pays. */
    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;
}
