-- =====================================================================
-- LOCAL ONLY facts about the seed data (loaded by the "local" profile).
--
-- The seed users, stores and delivery partners were inserted by SQL in their own services, so no
-- UserRegistered / StoreApproved / PartnerKycReviewed event exists for them. Without these rows the
-- dashboard would not count them. Everything created through the API arrives as events.
-- (The seed STOCK arrives as events: POST /internal/inventory/stock-snapshot.)
--
-- Repeatable migration (R__): Flyway runs it again whenever this file changes. INSERT IGNORE skips existing rows.
-- =====================================================================

INSERT IGNORE INTO user_facts (user_id, role, registered_at) VALUES
 (1, 'SUPER_ADMIN', '2026-01-01 00:00:00'), (2, 'ADMIN', '2026-01-01 00:00:00'), (3, 'VENDOR', '2026-01-01 00:00:00'),
 (4, 'CUSTOMER', '2026-01-01 00:00:00'), (5, 'DELIVERY_PARTNER', '2026-01-01 00:00:00'), (6, 'VENDOR', '2026-01-01 00:00:00'),
 (7, 'CUSTOMER', '2026-01-01 00:00:00'), (8, 'DELIVERY_PARTNER', '2026-01-01 00:00:00'), (9, 'CUSTOMER', '2026-01-01 00:00:00'),
 (10, 'VENDOR', '2026-01-01 00:00:00');

INSERT IGNORE INTO store_facts (store_id, name, owner_user_id, status) VALUES
 (1, 'Fresh Mart', 3, 'ACTIVE'),
 (2, 'Green Basket', 6, 'ACTIVE');

INSERT IGNORE INTO partner_facts (user_id, full_name, kyc_status) VALUES
 (5, 'Arjun Singh', 'APPROVED'),
 (8, 'Karthik Reddy', 'APPROVED');
