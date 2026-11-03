package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * What prepareMyCartAdd returns: the product the customer wants to add, with its packs and price, as they will see it
 * under the answer with a Confirm button. NOTHING is added: only the customer's click calls cart-service with their own
 * login.
 *
 * @param store   the product's store, e.g. "Fresh Mart"
 * @param items   the product to add (a list, like the other summaries: the chat builds its card from "items"); its
 *                quantity is the packs to add and its lineTotal what they cost
 * @param confirm what the Confirm button sends to cart-service (not for the answer)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartAddPreview(String store, List<CartSummary.Line> items, Confirm confirm) {

    /**
     * @param productId   the product to add
     * @param quantity    the packs to add
     * @param newQuantity the packs of this product in the cart after the click (what it has now plus quantity): the
     *                    button sets this total, so a second click or a retry never adds the packs twice
     */
    public record Confirm(long productId, int quantity, int newQuantity) {
    }
}
