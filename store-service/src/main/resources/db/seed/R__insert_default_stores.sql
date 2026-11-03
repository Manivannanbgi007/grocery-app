-- =====================================================================
-- LOCAL ONLY sample stores (loaded by the "local" profile).
--
-- Repeatable migration (R__): Flyway runs it again whenever this file
-- changes. INSERT IGNORE skips rows that already exist.
--
-- Owners are the seed vendors from auth-service (docs/seed-data.md):
--   store 1 Fresh Mart   -> user 3 (vendor1@grocery.com)
--   store 2 Green Basket -> user 6 (vendor2@grocery.com)
-- vendor3@grocery.com (user 10) has no store yet - use them to try the registration flow.
-- =====================================================================

INSERT IGNORE INTO stores (id, owner_user_id, name, description, phone, email, address_line1, address_line2, city, state, pincode,
                           latitude, longitude, status, status_reason, commission_percentage, accepting_orders,
                           approved_at, approved_by_user_id, created_at, updated_at, version) VALUES
 (1, 3, 'Fresh Mart', 'Fresh vegetables, fruits, dairy and daily essentials', '9000000003', 'freshmart@example.com',
     '45, 100 Feet Road, Indiranagar', 'Opposite CMH Hospital', 'Bengaluru', 'Karnataka', '560038',
     12.978400, 77.640800, 'ACTIVE', NULL, 10.00, TRUE, UTC_TIMESTAMP(6), 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (2, 6, 'Green Basket', 'Organic produce and household items', '9000000006', 'greenbasket@example.com',
     '12, 80 Feet Road, Koramangala 4th Block', NULL, 'Bengaluru', 'Karnataka', '560034',
     12.933600, 77.626500, 'ACTIVE', NULL, 8.50, TRUE, UTC_TIMESTAMP(6), 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);

-- Fresh Mart: 07:00 - 22:00 every day
INSERT IGNORE INTO store_opening_hours (store_id, day_of_week, opens_at, closes_at, closed) VALUES
 (1, 'MONDAY',    '07:00:00', '22:00:00', FALSE),
 (1, 'TUESDAY',   '07:00:00', '22:00:00', FALSE),
 (1, 'WEDNESDAY', '07:00:00', '22:00:00', FALSE),
 (1, 'THURSDAY',  '07:00:00', '22:00:00', FALSE),
 (1, 'FRIDAY',    '07:00:00', '22:00:00', FALSE),
 (1, 'SATURDAY',  '07:00:00', '22:00:00', FALSE),
 (1, 'SUNDAY',    '07:00:00', '22:00:00', FALSE);

-- Green Basket: 08:00 - 21:00 on weekdays and Saturday, Sunday morning only
INSERT IGNORE INTO store_opening_hours (store_id, day_of_week, opens_at, closes_at, closed) VALUES
 (2, 'MONDAY',    '08:00:00', '21:00:00', FALSE),
 (2, 'TUESDAY',   '08:00:00', '21:00:00', FALSE),
 (2, 'WEDNESDAY', '08:00:00', '21:00:00', FALSE),
 (2, 'THURSDAY',  '08:00:00', '21:00:00', FALSE),
 (2, 'FRIDAY',    '08:00:00', '21:00:00', FALSE),
 (2, 'SATURDAY',  '08:00:00', '21:00:00', FALSE),
 (2, 'SUNDAY',    '09:00:00', '14:00:00', FALSE);

INSERT IGNORE INTO store_delivery_pincodes (store_id, pincode) VALUES
 (1, '560038'), (1, '560008'), (1, '560071'),
 (2, '560034'), (2, '560095'), (2, '560047');
