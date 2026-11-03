-- Live delivery tracking: where the store (pickup) and the customer's address (drop) are, from OrderReadyForPickup.
-- Null when the store or the address has no coordinates (the map then shows what it knows).
-- The partner's live location is NOT stored here: it changes every few seconds and lives in Redis
-- (tracking/LiveLocationStore), so MySQL keeps only orders, assignments and statuses.
ALTER TABLE deliveries
    ADD COLUMN pickup_latitude  DECIMAL(9,6) NULL AFTER store_pincode,
    ADD COLUMN pickup_longitude DECIMAL(9,6) NULL AFTER pickup_latitude,
    ADD COLUMN drop_latitude    DECIMAL(9,6) NULL AFTER customer_pincode,
    ADD COLUMN drop_longitude   DECIMAL(9,6) NULL AFTER drop_latitude;
