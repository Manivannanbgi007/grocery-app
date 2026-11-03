package com.grocery.mcpclient.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A summary under the answer that the customer can confirm with a button. It is built in code from a tool result
 * (ChatActions), never from the model's text, so the card shows exactly what the shop's services said.
 *
 * kind PLACE_ORDER (from prepareMyOrder): the order summary. The website's Confirm button places the order with the
 * customer's own login (POST /api/v1/orders with addressId, paymentMethod, the expectedCheckoutFingerprint and an
 * Idempotency-Key made once per card); order-service refuses it when the cart changed since. Nothing is ordered before
 * that click.
 *
 * kind CANCEL_ORDER (from prepareMyOrderCancellation): one of the customer's orders. The Confirm button cancels it with
 * POST /api/v1/orders/{orderId}/cancel; order-service checks again whose order it is and whether it can still be
 * cancelled. Nothing is cancelled before that click.
 *
 * kind ADD_TO_CART (from prepareMyCartAdd): the products to add, each with its packs and price. The Confirm button
 * makes each line's newQuantity the cart's total of that product (POST /api/v1/cart/items for a product that is not in
 * the cart yet, PUT /api/v1/cart/items/{productId} for one that is), so a second click or a retry never adds twice;
 * cart-service checks the store, the stock and the limit of 20 again. Nothing is added before that click.
 *
 * kind REMOVE_FROM_CART (from prepareMyCartRemoval): a line of the customer's cart. The Confirm button takes it out with
 * DELETE /api/v1/cart/items/{productId} (the line's productId); cart-service only ever changes the cart of the token's
 * owner, and answers 404 when the product is no longer in the cart. Nothing is removed before that click.
 *
 * kind SET_CART_QUANTITY (from prepareMyCartQuantity): a line of the customer's cart as it is now, and its new quantity.
 * The Confirm button sets it with PUT /api/v1/cart/items/{productId} (a total, so a second click changes nothing more);
 * cart-service checks the stock and the limit of 20 again. Nothing is changed before that click.
 *
 * kind CLEAR_CART (from prepareMyCartClear): the whole cart. The Confirm button empties it with DELETE /api/v1/cart.
 * Nothing is removed before that click.
 *
 * @param recentOrder         only when the customer placed an order a few minutes ago ("Order #196 was placed 3 minutes ago.")
 * @param addressId           PLACE_ORDER: the delivery address (one of the customer's own; order-service checks it)
 * @param paymentMethod       PLACE_ORDER: COD or ONLINE
 * @param checkoutFingerprint PLACE_ORDER: the cart as summarised; a changed cart gets 409 instead of an order
 * @param orderId             CANCEL_ORDER: the order to cancel
 * @param status              CANCEL_ORDER: where the order is now, e.g. "Packed by the store"
 * @param refund              CANCEL_ORDER: what happens to the money, e.g. "The online payment of ₹138 will be refunded."
 * @param reason              CANCEL_ORDER: the customer's reason in their words, or null
 * @param newQuantity         SET_CART_QUANTITY: the new number of packs of the card's line
 */
@Schema(description = "A summary under the answer that the customer can confirm with a button")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatAction(
        @Schema(description = "What the button does", example = "PLACE_ORDER") String kind,
        String store,
        List<Line> items,
        String itemsTotal,
        String couponDiscount,
        String deliveryFee,
        String payable,
        String deliverTo,
        String payment,
        String recentOrder,
        Long addressId,
        String paymentMethod,
        String checkoutFingerprint,
        Long orderId,
        String status,
        String refund,
        String reason,
        Integer newQuantity) {

    public static final String PLACE_ORDER = "PLACE_ORDER";
    public static final String ADD_TO_CART = "ADD_TO_CART";
    public static final String CANCEL_ORDER = "CANCEL_ORDER";
    public static final String REMOVE_FROM_CART = "REMOVE_FROM_CART";
    public static final String SET_CART_QUANTITY = "SET_CART_QUANTITY";
    public static final String CLEAR_CART = "CLEAR_CART";

    /**
     * One line of the order or of the cart: "2 x Tomato (1 kg): ₹72"
     *
     * @param quantity    the packs of the line; ADD_TO_CART: the packs to add (and lineTotal what they cost)
     * @param productId   ADD_TO_CART, REMOVE_FROM_CART and SET_CART_QUANTITY: the product the Confirm button adds, takes
     *                    out or changes
     * @param newQuantity ADD_TO_CART: the packs of this product in the cart after the click (what it has now plus quantity)
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Line(String name, String packSize, int quantity, String lineTotal, Long productId, Integer newQuantity) {

        public Line(String name, String packSize, int quantity, String lineTotal, Long productId) {
            this(name, packSize, quantity, lineTotal, productId, null);
        }

        public Line(String name, String packSize, int quantity, String lineTotal) {
            this(name, packSize, quantity, lineTotal, null, null);
        }
    }
}
