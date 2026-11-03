-- Phase 9: who delivers the order (from delivery-service's DeliveryAssigned event).
ALTER TABLE orders
    ADD COLUMN delivery_partner_name  VARCHAR(100) NULL AFTER delivered_at,
    ADD COLUMN delivery_partner_phone VARCHAR(15)  NULL AFTER delivery_partner_name;
