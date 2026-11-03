-- Where the conversation is in buying the cart (CheckoutStep): the chat asked how to pay, or showed the order summary.
-- NULL when no checkout is going on. Kept with the conversation, so it belongs to its owner only. The order and its
-- payment themselves are order-service's and payment-service's.
ALTER TABLE conversations
    ADD COLUMN checkout_step VARCHAR(30) NULL COMMENT 'AWAITING_PAYMENT_METHOD or AWAITING_CONFIRMATION';
