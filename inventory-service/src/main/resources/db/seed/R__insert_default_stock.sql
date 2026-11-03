-- =====================================================================
-- LOCAL ONLY sample stock (loaded by the "local" profile).
--
-- Repeatable migration (R__): Flyway runs it again whenever this file
-- changes. INSERT IGNORE skips rows that already exist.
--
-- Products are the seed catalog from product-service (docs/seed-data.md):
--   store 1 Fresh Mart   -> products 1-10 and 17
--   store 2 Green Basket -> products 11-16
-- Interesting rows for testing: 7 and 14 are LOW (at or below threshold 5),
-- 13 and 17 are OUT OF STOCK.
--
-- SQL inserts publish no events. After the first start, call
-- POST /internal/inventory/stock-snapshot so product-read-service learns the stock.
-- =====================================================================

INSERT IGNORE INTO stock_items (product_id, store_id, quantity_on_hand, reserved_quantity, low_stock_threshold,
                                created_at, updated_at, version) VALUES
 -- store 1 Fresh Mart
 (1,  1, 120, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Tomato
 (2,  1, 200, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Onion
 (3,  1, 150, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Potato
 (4,  1,  40, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Banana Robusta
 (5,  1,  25, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Apple Shimla
 (6,  1,  60, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Amul Taaza Toned Milk
 (7,  1,   3, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Amul Butter            LOW
 (8,  1,  30, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Farm Eggs
 (9,  1,  80, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Lay's Classic Salted
 (10, 1,  12, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Surf Excel Matic Liquid
 (17, 1,   0, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Mango Alphonso (INACTIVE product) OUT OF STOCK
 -- store 2 Green Basket
 (11, 2,  20, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Organic Spinach
 (12, 2,  35, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Organic Carrot
 (13, 2,   0, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Pomegranate            OUT OF STOCK
 (14, 2,   4, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Nandini Curd           LOW
 (15, 2,  50, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),   -- Haldiram's Aloo Bhujia
 (16, 2,  18, 0, 5, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);   -- Vim Dishwash Liquid
