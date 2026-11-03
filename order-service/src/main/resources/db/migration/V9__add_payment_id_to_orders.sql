-- The payment in payment-service that settled the order (PaymentConfirmed.paymentId). Shown on the invoice and saved
-- with it, so every invoice names its payment. Empty for orders confirmed before this column existed.
ALTER TABLE orders
    ADD COLUMN payment_id BIGINT NULL AFTER payment_status;
