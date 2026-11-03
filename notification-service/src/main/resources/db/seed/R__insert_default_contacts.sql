-- =====================================================================
-- LOCAL ONLY contact book of the seed users and stores (loaded by the "local" profile).
--
-- The seed users and stores were inserted by SQL in auth-service and store-service, so no
-- UserRegistered / StoreApproved event exists for them. Without these rows notification-service
-- would not know their email addresses. Users and stores created through the API arrive as events.
--
-- Repeatable migration (R__): Flyway runs it again whenever this file changes. INSERT IGNORE skips existing rows.
-- =====================================================================

INSERT IGNORE INTO contacts (user_id, full_name, email, phone, role) VALUES
 (1,  'Super Admin',    'superadmin@grocery.com', '9000000001', 'SUPER_ADMIN'),
 (2,  'Platform Admin', 'admin@grocery.com',      '9000000002', 'ADMIN'),
 (3,  'Ravi Kumar',     'vendor1@grocery.com',    '9000000003', 'VENDOR'),
 (4,  'Priya Sharma',   'customer1@grocery.com',  '9000000004', 'CUSTOMER'),
 (5,  'Arjun Singh',    'delivery1@grocery.com',  '9000000005', 'DELIVERY_PARTNER'),
 (6,  'Meena Iyer',     'vendor2@grocery.com',    '9000000006', 'VENDOR'),
 (7,  'Rahul Verma',    'customer2@grocery.com',  '9000000007', 'CUSTOMER'),
 (8,  'Karthik Reddy',  'delivery2@grocery.com',  '9000000008', 'DELIVERY_PARTNER'),
 (9,  'Anita Desai',    'customer3@grocery.com',  '9000000009', 'CUSTOMER'),
 (10, 'Suresh Patel',   'vendor3@grocery.com',    '9000000010', 'VENDOR');

INSERT IGNORE INTO store_contacts (store_id, owner_user_id, store_name) VALUES
 (1, 3, 'Fresh Mart'),
 (2, 6, 'Green Basket');
