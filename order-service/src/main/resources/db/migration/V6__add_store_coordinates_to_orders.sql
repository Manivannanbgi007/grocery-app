-- Delivery tracking: where the store was when the order was placed (copied like its address), so the
-- OrderReadyForPickup event can give delivery-service the pickup point for the map. Null when the store has no coordinates.
ALTER TABLE orders
    ADD COLUMN store_latitude  DECIMAL(9,6) NULL AFTER commission_percentage,
    ADD COLUMN store_longitude DECIMAL(9,6) NULL AFTER store_latitude;
