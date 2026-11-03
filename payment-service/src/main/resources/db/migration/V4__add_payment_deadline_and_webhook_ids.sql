-- Phase 10: an online payment must be paid before a deadline (PaymentTimeoutJob fails it afterwards).
ALTER TABLE payments
    ADD COLUMN pay_before DATETIME(6) NULL COMMENT 'ONLINE: the customer must pay before this time' AFTER gateway_payment_id,
    ADD INDEX idx_payments_status_pay_before (status, pay_before);

-- Razorpay webhook event ids (evt_...) are also recorded here to skip duplicate webhooks; leave room for them.
ALTER TABLE processed_event MODIFY event_id VARCHAR(100) NOT NULL;
