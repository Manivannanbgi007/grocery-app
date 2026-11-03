package com.grocery.cart.service;

import com.grocery.cart.dto.response.CheckoutSummaryResponse;

/**
 * Checks a cart against the CURRENT catalog before it becomes an order.
 */
public interface CheckoutService {

    /**
     * Refreshes every item with today's name and prices (one call to product-read-service), and lists
     * whatever blocks the order: unavailable or out-of-stock products, an unusable coupon, the minimum order value.
     */
    CheckoutSummaryResponse prepareCheckout(Long customerUserId);
}
