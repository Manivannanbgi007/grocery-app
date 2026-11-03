package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * What prepareMyCartQuantity returns: the line of the customer's own cart as it is now, and the new number of packs,
 * as they will see it under the answer with a Confirm button. NOTHING is changed: only the customer's click calls
 * cart-service (PUT /api/v1/cart/items/{productId} with the new quantity) with their own login.
 *
 * @param store   the cart's store, e.g. "Fresh Mart"
 * @param items   the line as it is now (a list, like the other summaries: the chat builds its card from "items")
 * @param confirm what the Confirm button sends to cart-service (not for the answer)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartQuantityPreview(String store, List<CartSummary.Line> items, Confirm confirm) {

    /**
     * @param productId the product whose packs change
     * @param quantity  the new number of packs (a total, not an addition)
     */
    public record Confirm(long productId, int quantity) {
    }
}
