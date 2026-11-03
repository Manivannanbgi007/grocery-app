package com.grocery.cart.service;

import com.grocery.cart.dto.request.AddCartItemRequest;
import com.grocery.cart.dto.request.ApplyCouponRequest;
import com.grocery.cart.dto.request.UpdateCartItemRequest;
import com.grocery.cart.dto.response.CartResponse;

/**
 * The customer's own cart. Every method takes the customer's user id (X-User-Id).
 */
public interface CartService {

    CartResponse getCart(Long customerUserId);

    /**
     * Adds a product (or more units of it).
     *
     * @param replaceCart true = if the cart holds items of another store, empty it first
     */
    CartResponse addItem(Long customerUserId, AddCartItemRequest request, boolean replaceCart);

    CartResponse updateItemQuantity(Long customerUserId, Long productId, UpdateCartItemRequest request);

    CartResponse removeItem(Long customerUserId, Long productId);

    CartResponse clearCart(Long customerUserId);

    CartResponse applyCoupon(Long customerUserId, ApplyCouponRequest request);

    CartResponse removeCoupon(Long customerUserId);
}
