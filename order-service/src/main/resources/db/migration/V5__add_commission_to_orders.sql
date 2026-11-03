-- Phase 11: the platform commission of the store when the order was placed (commission reports).
ALTER TABLE orders ADD COLUMN commission_percentage DECIMAL(5,2) NULL COMMENT 'Platform commission in percent' AFTER store_pincode;
