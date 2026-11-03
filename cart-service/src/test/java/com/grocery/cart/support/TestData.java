package com.grocery.cart.support;

import com.grocery.cart.client.dto.CatalogProduct;
import com.grocery.cart.entity.Coupon;
import com.grocery.cart.enums.DiscountType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Small builders for test objects, so every test reads like the rule it checks.
 */
public final class TestData {

    public static final Instant NOW = Instant.parse("2026-10-26T10:00:00Z");

    private TestData() {
    }

    /** An ACTIVE product without a product discount and without tax. */
    public static CatalogProduct product(long id, long storeId, String name, String sellingPrice, int availableQuantity) {
        return new CatalogProduct(id, storeId, name, "KG", BigDecimal.ONE, new BigDecimal(sellingPrice), new BigDecimal(sellingPrice),
                BigDecimal.ZERO, List.of(), "ACTIVE", availableQuantity);
    }

    public static CatalogProduct productWithStatus(long id, long storeId, String name, String status) {
        return new CatalogProduct(id, storeId, name, "KG", BigDecimal.ONE, new BigDecimal("10.00"), new BigDecimal("10.00"),
                BigDecimal.ZERO, List.of(), status, 100);
    }

    /** A platform coupon valid during 2026, used at most once per customer. */
    public static Coupon flatCoupon(String code, String amount, String minOrderAmount) {
        return coupon(code, DiscountType.FLAT, amount, null, minOrderAmount);
    }

    public static Coupon percentageCoupon(String code, String percent, String maxDiscount, String minOrderAmount) {
        return coupon(code, DiscountType.PERCENTAGE, percent, maxDiscount, minOrderAmount);
    }

    private static Coupon coupon(String code, DiscountType type, String value, String maxDiscount, String minOrderAmount) {
        Coupon coupon = new Coupon();
        coupon.setId(1L);
        coupon.setCode(code);
        coupon.setDescription(code + " coupon");
        coupon.setDiscountType(type);
        coupon.setDiscountValue(new BigDecimal(value));
        coupon.setMaxDiscountAmount(maxDiscount == null ? null : new BigDecimal(maxDiscount));
        coupon.setMinOrderAmount(new BigDecimal(minOrderAmount));
        coupon.setValidFrom(Instant.parse("2026-01-01T00:00:00Z"));
        coupon.setValidUntil(Instant.parse("2026-12-31T23:59:59Z"));
        coupon.setPerCustomerLimit(1);
        coupon.setActive(true);
        coupon.setCreatedByUserId(2L);
        return coupon;
    }
}
