package com.grocery.order.dto.request;

import com.grocery.order.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Turn my cart into an order. The items and prices come from the cart, not from this request.
 *
 * @param addressId                   one of my addresses (customer-service)
 * @param note                        optional, e.g. "Please ring the bell twice"
 * @param expectedCheckoutFingerprint optional: the checkoutFingerprint of the summary the customer confirmed (the chat's
 *                                    Confirm button); when the cart or its prices changed since, the order is refused
 *                                    (409), so nobody gets an order they did not see
 */
public record PlaceOrderRequest(
        @NotNull @Positive Long addressId,
        @NotNull PaymentMethod paymentMethod,
        @Size(max = 250) String note,
        @Size(max = 64) String expectedCheckoutFingerprint) {

    /** An order without a summary to compare with (the website's checkout page) */
    public PlaceOrderRequest(Long addressId, PaymentMethod paymentMethod, String note) {
        this(addressId, paymentMethod, note, null);
    }
}
