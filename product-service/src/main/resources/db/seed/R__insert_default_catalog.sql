-- =====================================================================
-- LOCAL ONLY sample catalog (loaded by the "local" profile).
--
-- Repeatable migration (R__): Flyway runs it again whenever this file
-- changes. INSERT IGNORE skips rows that already exist.
--
-- Stores come from store-service (docs/seed-data.md):
--   store 1 = Fresh Mart, store 2 = Green Basket
-- selling_price = price - (price * discount_percentage / 100), rounded to 2 decimals
-- =====================================================================

INSERT IGNORE INTO categories (id, name, slug, description, image_url, display_order, active, created_at, updated_at, version) VALUES
 (1, 'Vegetables',          'vegetables',          'Fresh vegetables delivered daily', NULL, 1, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (2, 'Fruits',              'fruits',              'Seasonal and exotic fruits',       NULL, 2, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (3, 'Dairy & Eggs',        'dairy-eggs',          'Milk, curd, butter, cheese, eggs', NULL, 3, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (4, 'Snacks & Beverages',  'snacks-beverages',    'Chips, namkeen, biscuits, drinks', NULL, 4, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (5, 'Household',           'household',           'Cleaning and home care',           NULL, 5, TRUE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);

INSERT IGNORE INTO products (id, store_id, category_id, name, description, brand, sku, unit, unit_quantity,
                             price, discount_percentage, selling_price, tax_percentage, image_urls, status,
                             created_at, updated_at, version) VALUES
 -- Fresh Mart (store 1)
 (1,  1, 1, 'Tomato',                   'Farm fresh red tomatoes',              NULL,        'FM-VEG-001', 'KG',    1.000,  40.00, 10.00,  36.00,  0.00, '[]', 'ACTIVE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (2,  1, 1, 'Onion',                    'Medium size red onions',               NULL,        'FM-VEG-002', 'KG',    1.000,  35.00,  0.00,  35.00,  0.00, '[]', 'ACTIVE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (3,  1, 1, 'Potato',                   'Fresh potatoes',                       NULL,        'FM-VEG-003', 'KG',    1.000,  30.00,  5.00,  28.50,  0.00, '[]', 'ACTIVE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (4,  1, 2, 'Banana Robusta',           'Ripe robusta bananas',                 NULL,        'FM-FRT-001', 'DOZEN', 1.000,  60.00,  0.00,  60.00,  0.00, '[]', 'ACTIVE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (5,  1, 2, 'Apple Shimla',             'Crisp and juicy Shimla apples',        NULL,        'FM-FRT-002', 'KG',    1.000, 180.00, 10.00, 162.00,  0.00, '[]', 'ACTIVE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (6,  1, 3, 'Amul Taaza Toned Milk',    'Pasteurised toned milk',               'Amul',      'FM-DRY-001', 'ML',  500.000,  27.00,  0.00,  27.00,  0.00, '[]', 'ACTIVE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (7,  1, 3, 'Amul Butter',              'Pasteurised salted butter',            'Amul',      'FM-DRY-002', 'G',   100.000,  56.00,  0.00,  56.00, 12.00, '[]', 'ACTIVE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (8,  1, 3, 'Farm Eggs',                'Fresh white eggs',                     NULL,        'FM-DRY-003', 'PIECE',  6.000,  48.00,  0.00,  48.00,  0.00, '[]', 'ACTIVE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (9,  1, 4, 'Lay''s Classic Salted',    'Potato chips, classic salted',         'Lay''s',    'FM-SNK-001', 'G',    52.000,  20.00,  0.00,  20.00, 12.00, '[]', 'ACTIVE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (10, 1, 5, 'Surf Excel Matic Liquid',  'Front load washing liquid',            'Surf Excel','FM-HHD-001', 'L',     1.000, 230.00, 15.00, 195.50, 18.00, '[]', 'ACTIVE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (17, 1, 2, 'Mango Alphonso',           'Ratnagiri Alphonso (out of season)',   NULL,        'FM-FRT-003', 'DOZEN', 1.000, 350.00,  0.00, 350.00,  0.00, '[]', 'INACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 -- Green Basket (store 2)
 (11, 2, 1, 'Organic Spinach',          'Pesticide-free spinach leaves',        'Organic Tattva', 'GB-VEG-001', 'G', 250.000,  30.00,  0.00,  30.00,  0.00, '[]', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (12, 2, 1, 'Organic Carrot',           'Sweet organic carrots',                'Organic Tattva', 'GB-VEG-002', 'G', 500.000,  45.00,  5.00,  42.75,  0.00, '[]', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (13, 2, 2, 'Pomegranate',              'Bhagwa pomegranates',                  NULL,             'GB-FRT-001', 'KG',  1.000, 200.00, 20.00, 160.00,  0.00, '[]', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (14, 2, 3, 'Nandini Curd',             'Fresh set curd',                       'Nandini',        'GB-DRY-001', 'G', 500.000,  30.00,  0.00,  30.00,  5.00, '[]', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (15, 2, 4, 'Haldiram''s Aloo Bhujia',  'Crispy potato and gram flour noodles', 'Haldiram''s',    'GB-SNK-001', 'G', 200.000,  55.00,  0.00,  55.00, 12.00, '[]', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (16, 2, 5, 'Vim Dishwash Liquid',      'Lemon dishwash gel',                   'Vim',            'GB-HHD-001', 'ML',500.000, 110.00, 10.00,  99.00, 18.00, '[]', 'ACTIVE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);
