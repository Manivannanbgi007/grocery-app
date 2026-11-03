package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * What prepareMyCartRemoval returns: the line of the customer's own cart they want to take out, as they will see it
 * under the answer with a Confirm button. NOTHING is removed: only the customer's click calls cart-service
 * (DELETE /api/v1/cart/items/{productId}) with their own login.
 *
 * @param store   the cart's store, e.g. "Fresh Mart"
 * @param items   the line to take out (a list, like the other summaries: the chat builds its card from "items")
 * @param confirm what the Confirm button sends to cart-service (not for the answer)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartRemovalPreview(String store, List<CartSummary.Line> items, Confirm confirm) {

    /** @param productId the product to take out of the cart */
    public record Confirm(long productId) {
    }
}
