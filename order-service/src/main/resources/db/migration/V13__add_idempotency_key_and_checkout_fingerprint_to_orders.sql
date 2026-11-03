-- How an order was asked for (the chat's Confirm button sends both; the website's checkout neither):
-- idempotency_key: the client's Idempotency-Key header. A repeat of the request with the same key (after a timeout, a
--                  double click) gets the order it placed, never a second one. Unique per customer; NULLs do not clash.
-- checkout_fingerprint: cart-service's fingerprint of the cart the order was made of. The same cart ordered again a
--                  moment later is refused as a double order.
ALTER TABLE orders
    ADD COLUMN idempotency_key VARCHAR(64) NULL,
    ADD COLUMN checkout_fingerprint VARCHAR(64) NULL;

CREATE UNIQUE INDEX uk_orders_customer_idempotency_key ON orders (customer_user_id, idempotency_key);
CREATE INDEX idx_orders_customer_checkout_fingerprint ON orders (customer_user_id, checkout_fingerprint);
