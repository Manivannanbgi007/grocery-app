-- =====================================================================
-- LOCAL ONLY delivery zones and partners (loaded by the "local" profile).
--
-- Repeatable migration (R__): Flyway runs it again whenever this file changes. INSERT IGNORE skips existing rows.
--
-- Zones match the delivery areas of the seed stores (docs/seed-data.md):
--   zone 1 Indiranagar  560038, 560008, 560071  (Fresh Mart, store 1)
--   zone 2 Koramangala  560034, 560095, 560047  (Green Basket, store 2)
-- Partners (seed users of auth-service, both APPROVED):
--   delivery1 (user 5) Arjun Singh    zone 1
--   delivery2 (user 8) Karthik Reddy  zone 2
-- They are OFFLINE until they call POST /api/v1/delivery/availability/online.
-- =====================================================================

INSERT IGNORE INTO delivery_zones (id, name, city, partner_payout, active, created_at, updated_at, version) VALUES
 (1, 'Indiranagar', 'Bengaluru', 40.00, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (2, 'Koramangala', 'Bengaluru', 40.00, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);

INSERT IGNORE INTO delivery_zone_pincodes (zone_id, pincode) VALUES
 (1, '560038'), (1, '560008'), (1, '560071'),
 (2, '560034'), (2, '560095'), (2, '560047');

INSERT IGNORE INTO delivery_partners (id, user_id, full_name, phone, vehicle_type, vehicle_number, driving_license_number,
                                      zone_id, kyc_status, kyc_note, created_at, updated_at, version) VALUES
 (1, 5, 'Arjun Singh',   '9000000005', 'SCOOTER',   'KA01AB1234', 'KA0120190012345', 1, 'APPROVED', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (2, 8, 'Karthik Reddy', '9000000008', 'MOTORBIKE', 'KA05CD5678', 'KA0520180067890', 2, 'APPROVED', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);
