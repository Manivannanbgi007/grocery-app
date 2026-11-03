package com.grocery.mcpserver.tool.result;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * What the cart tools (prepareMyCartAdd, prepareMyCartQuantity, prepareMyCartRemoval) return as data when the customer
 * must decide something first (status CHOOSE):
 * - options: which product they mean, when several fit their words;
 * - amountFor: how much they want of the one product that fits ("add tomato" without an amount). mcp-client-service
 *   remembers this product, so the customer's next message ("3 kg") adds exactly it.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CartChange(List<ProductSummary> options, ProductSummary amountFor) {

    public static CartChange choices(List<ProductSummary> options) {
        return new CartChange(options, null);
    }

    public static CartChange amountFor(ProductSummary product) {
        return new CartChange(null, product);
    }
}
