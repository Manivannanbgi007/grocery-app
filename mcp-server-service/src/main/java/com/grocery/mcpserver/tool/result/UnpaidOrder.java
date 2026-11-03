package com.grocery.mcpserver.tool.result;

/**
 * What prepareMyOrder returns as data when the customer asks to pay again ("proceed to pay") after their order was
 * placed: the cart is empty then, and the newest order is an online order that still waits for its payment. Nothing new
 * is ordered; mcp-client-service shows a "Pay Now" button for paymentUrl.
 *
 * @param orderNumber the order waiting for its payment
 * @param payable     what to pay, e.g. "₹138"
 * @param paymentUrl  order-service's own payment address (the order page, where the payment window opens), never made up
 */
public record UnpaidOrder(long orderNumber, String payable, String paymentUrl) {
}
