package com.grocery.mcpserver.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * The customer's cart checked against today's catalog (cart-service, GET /api/v1/cart/checkout): what an order of it
 * would contain right now, only the fields the tools need.
 *
 * @param problems            what must be fixed before the cart can be ordered ("Tomato is out of stock"); empty when ready
 * @param checkoutFingerprint changes with any product, quantity, price, coupon or total; order-service places an order
 *                            from a summary only while the cart still has this fingerprint
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CheckoutData(Long storeId, List<CartData.Item> items, String couponCode, CartData.Totals totals,
                           boolean readyForCheckout, List<String> problems, String checkoutFingerprint) {
}
