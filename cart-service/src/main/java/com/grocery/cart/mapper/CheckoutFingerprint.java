package com.grocery.cart.mapper;

import com.grocery.cart.dto.response.CartItemResponse;
import com.grocery.cart.dto.response.CartTotalsResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

/**
 * A short fingerprint of exactly what an order of this cart would contain: the store, every product with its quantity
 * and price, the coupon and the totals. Any change of these gives another fingerprint.
 *
 * The chat's order summary carries it, and order-service compares it with the cart's fingerprint when the customer
 * presses Confirm: an order is never placed for a cart (or prices) that changed after the customer saw the summary.
 * order-service also stores it with the order, to notice the same cart ordered twice a moment apart.
 */
final class CheckoutFingerprint {

    /** 16 hex characters (64 bits): plenty to tell two carts apart, short enough to send around */
    private static final int LENGTH = 16;

    private CheckoutFingerprint() {
    }

    static String of(Long storeId, List<CartItemResponse> items, String couponCode, CartTotalsResponse totals) {
        StringBuilder text = new StringBuilder().append("store=").append(storeId).append("|coupon=").append(couponCode);
        items.stream()
                .sorted(Comparator.comparing(CartItemResponse::productId))
                .forEach(item -> text.append("|product=").append(item.productId()).append('x').append(item.quantity())
                        .append('@').append(plain(item.sellingPrice())));
        text.append("|items=").append(plain(totals.itemsTotal()))
                .append("|coupon-discount=").append(plain(totals.couponDiscount()))
                .append("|delivery=").append(plain(totals.deliveryFee()))
                .append("|payable=").append(plain(totals.payableAmount()));
        return sha256(text.toString()).substring(0, LENGTH);
    }

    /** 56.00 and 56 are the same price */
    private static String plain(BigDecimal amount) {
        return amount == null ? "" : amount.stripTrailingZeros().toPlainString();
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", ex);
        }
    }
}
