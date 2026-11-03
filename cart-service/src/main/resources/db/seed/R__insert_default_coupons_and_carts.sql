-- =====================================================================
-- LOCAL ONLY sample coupons and a cart (loaded by the "local" profile).
--
-- Repeatable migration (R__): Flyway runs it again whenever this file
-- changes. INSERT IGNORE skips rows that already exist.
--
-- Coupons (docs/seed-data.md):
--   WELCOME50  platform  50.00 off, items total >= 299, once per customer
--   SAVE10     platform  10 % off (max 100.00), items total >= 199
--   FRESH20    store 1   20 % off (max 60.00), items total >= 249
--   GREEN30    store 2   30.00 off, items total >= 199
--   SUMMER25   platform  EXPIRED (to try the expiry rule)
--   MANGO15    store 1   INACTIVE (switched off by the vendor)
-- Cart: customer1 (user 4) has 3 products of Fresh Mart (store 1), items total 201.00.
-- =====================================================================

INSERT IGNORE INTO coupons (id, code, description, store_id, discount_type, discount_value, max_discount_amount, min_order_amount,
                            valid_from, valid_until, usage_limit, used_count, per_customer_limit, active, created_by_user_id,
                            created_at, updated_at, version) VALUES
 (1, 'WELCOME50', 'Flat 50 off your first order of 299 or more', NULL, 'FLAT', 50.00, NULL, 299.00,
     '2026-01-01 00:00:00', '2027-12-31 23:59:59', 1000, 0, 1, TRUE, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (2, 'SAVE10', '10% off (up to 100) on orders of 199 or more', NULL, 'PERCENTAGE', 10.00, 100.00, 199.00,
     '2026-01-01 00:00:00', '2027-12-31 23:59:59', NULL, 0, 5, TRUE, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (3, 'FRESH20', 'Fresh Mart: 20% off (up to 60) on 249 or more', 1, 'PERCENTAGE', 20.00, 60.00, 249.00,
     '2026-01-01 00:00:00', '2027-12-31 23:59:59', 500, 0, 2, TRUE, 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (4, 'GREEN30', 'Green Basket: flat 30 off on 199 or more', 2, 'FLAT', 30.00, NULL, 199.00,
     '2026-01-01 00:00:00', '2027-12-31 23:59:59', NULL, 0, 3, TRUE, 6, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (5, 'SUMMER25', 'Summer sale: 25% off (ended)', NULL, 'PERCENTAGE', 25.00, 150.00, 0.00,
     '2026-04-01 00:00:00', '2026-06-30 23:59:59', NULL, 0, 1, TRUE, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (6, 'MANGO15', 'Fresh Mart: 15% off mangoes (switched off)', 1, 'PERCENTAGE', 15.00, NULL, 0.00,
     '2026-01-01 00:00:00', '2027-12-31 23:59:59', NULL, 0, 1, FALSE, 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);

-- customer1 (user 4): Tomato x2, Amul Taaza Toned Milk x3, Farm Eggs x1 from Fresh Mart = 72 + 81 + 48 = 201.00
INSERT IGNORE INTO carts (id, customer_user_id, store_id, coupon_code, created_at, updated_at, version) VALUES
 (1, 4, 1, NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);

INSERT IGNORE INTO cart_items (id, cart_id, product_id, product_name, unit, unit_quantity, image_url, price, selling_price,
                               tax_percentage, quantity, created_at, updated_at, version) VALUES
 (1, 1, 1, 'Tomato',                'KG',      1.000, NULL, 40.00, 36.00, 0.00, 2, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (2, 1, 6, 'Amul Taaza Toned Milk', 'ML',    500.000, NULL, 27.00, 27.00, 0.00, 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (3, 1, 8, 'Farm Eggs',             'PIECE',   6.000, NULL, 48.00, 48.00, 0.00, 1, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);
