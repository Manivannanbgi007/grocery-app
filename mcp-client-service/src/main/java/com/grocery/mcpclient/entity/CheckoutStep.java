package com.grocery.mcpclient.entity;

/**
 * Where a conversation is in buying the cart, as far as the chat is concerned (Conversation.checkoutStep). The chat
 * keeps only the steps before the order exists; everything after it is the backend's, which is the source of truth:
 *
 * <pre>
 * "Proceed to pay"           -> prepareMyOrder reads and checks the cart (cart-service: items, stock, totals, minimum)
 *   no way to pay named      -> AWAITING_PAYMENT_METHOD   "How would you like to pay: cash on delivery, or online ...?"
 *   "Online" / "COD"         -> AWAITING_CONFIRMATION     the order summary with its Confirm button (the way to pay on it)
 * Confirm (the customer)     -> order-service places the order (Idempotency-Key, checkout fingerprint); the step ends
 * online: Pay Now            -> the order page opens Razorpay; payment-service marks it paid from Razorpay's signed webhook
 * </pre>
 *
 * Null when no checkout is going on. Any other message ends a step (the model answers it).
 */
public enum CheckoutStep {

    /** The chat asked how the customer wants to pay: "Online", "COD", "pay after delivery" answers it */
    AWAITING_PAYMENT_METHOD,

    /** The order summary is shown with its Confirm button: another way to pay ("COD instead") makes a new summary */
    AWAITING_CONFIRMATION
}
