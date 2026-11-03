-- =====================================================================
-- LOCAL ONLY sample users (loaded by the "local" profile).
--
-- Repeatable migration (R__): Flyway runs it again whenever this file
-- changes. INSERT IGNORE skips rows that already exist, so it is safe
-- to run many times.
--
-- Password for every user:  Password@123
-- The ids are fixed so other services' seed data can refer to them
-- (see docs/seed-data.md).
-- =====================================================================

INSERT IGNORE INTO users (id, full_name, email, phone, password_hash, role, status, store_id, created_at, updated_at, version) VALUES
 (1,  'Super Admin',    'superadmin@grocery.com', '9000000001', '$2a$10$9l8uTedgXGSGwUG./KcMjOUcPHDL8qX9SiaE8gL/Oo6qm4bXOWKxy', 'SUPER_ADMIN',      'ACTIVE',   NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (2,  'Platform Admin', 'admin@grocery.com',      '9000000002', '$2a$10$9l8uTedgXGSGwUG./KcMjOUcPHDL8qX9SiaE8gL/Oo6qm4bXOWKxy', 'ADMIN',            'ACTIVE',   NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (3,  'Ravi Kumar',     'vendor1@grocery.com',    '9000000003', '$2a$10$9l8uTedgXGSGwUG./KcMjOUcPHDL8qX9SiaE8gL/Oo6qm4bXOWKxy', 'VENDOR',           'ACTIVE',   1,    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (4,  'Priya Sharma',   'customer1@grocery.com',  '9000000004', '$2a$10$9l8uTedgXGSGwUG./KcMjOUcPHDL8qX9SiaE8gL/Oo6qm4bXOWKxy', 'CUSTOMER',         'ACTIVE',   NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (5,  'Arjun Singh',    'delivery1@grocery.com',  '9000000005', '$2a$10$9l8uTedgXGSGwUG./KcMjOUcPHDL8qX9SiaE8gL/Oo6qm4bXOWKxy', 'DELIVERY_PARTNER', 'ACTIVE',   NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (6,  'Meena Iyer',     'vendor2@grocery.com',    '9000000006', '$2a$10$9l8uTedgXGSGwUG./KcMjOUcPHDL8qX9SiaE8gL/Oo6qm4bXOWKxy', 'VENDOR',           'ACTIVE',   2,    UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (7,  'Rahul Verma',    'customer2@grocery.com',  '9000000007', '$2a$10$9l8uTedgXGSGwUG./KcMjOUcPHDL8qX9SiaE8gL/Oo6qm4bXOWKxy', 'CUSTOMER',         'ACTIVE',   NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (8,  'Karthik Reddy',  'delivery2@grocery.com',  '9000000008', '$2a$10$9l8uTedgXGSGwUG./KcMjOUcPHDL8qX9SiaE8gL/Oo6qm4bXOWKxy', 'DELIVERY_PARTNER', 'ACTIVE',   NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (9,  'Anita Desai',    'customer3@grocery.com',  '9000000009', '$2a$10$9l8uTedgXGSGwUG./KcMjOUcPHDL8qX9SiaE8gL/Oo6qm4bXOWKxy', 'CUSTOMER',         'DISABLED', NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (10, 'Suresh Patel',   'vendor3@grocery.com',    '9000000010', '$2a$10$9l8uTedgXGSGwUG./KcMjOUcPHDL8qX9SiaE8gL/Oo6qm4bXOWKxy', 'VENDOR',           'ACTIVE',   NULL, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);
