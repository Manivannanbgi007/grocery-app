-- =====================================================================
-- LOCAL ONLY sample customers (loaded by the "local" profile).
--
-- Repeatable migration (R__): Flyway runs it again whenever this file
-- changes. INSERT IGNORE skips rows that already exist.
--
-- user_id = the seed CUSTOMER users of auth-service (docs/seed-data.md).
-- Customers registered later through the API are created from the
-- UserRegistered event instead.
-- =====================================================================

INSERT IGNORE INTO customers (id, user_id, full_name, email, phone, date_of_birth, gender, created_at, updated_at, version) VALUES
 (1, 4, 'Priya Sharma', 'customer1@grocery.com', '9000000004', '1994-05-17', 'FEMALE', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (2, 7, 'Rahul Verma',  'customer2@grocery.com', '9000000007', '1990-11-02', 'MALE',   UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (3, 9, 'Anita Desai',  'customer3@grocery.com', '9000000009', NULL,         NULL,     UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);

INSERT IGNORE INTO addresses (id, customer_id, label, contact_name, contact_phone, address_line1, address_line2, landmark,
                              city, state, pincode, latitude, longitude, is_default, created_at, updated_at, version) VALUES
 (1, 1, 'HOME', 'Priya Sharma', '9000000004', 'Flat 302, Lakeview Apartments', '2nd Main Road, HAL 2nd Stage', 'Near Indiranagar Metro',
     'Bengaluru', 'Karnataka', '560038', 12.971900, 77.641200, TRUE,  UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (2, 1, 'WORK', 'Priya Sharma', '9000000004', 'Tower B, 5th Floor, Tech Park', 'Koramangala 4th Block', NULL,
     'Bengaluru', 'Karnataka', '560034', 12.933700, 77.627000, FALSE, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0),
 (3, 2, 'HOME', 'Rahul Verma',  '9000000007', '18, Cambridge Layout', 'Ulsoor', NULL,
     'Bengaluru', 'Karnataka', '560008', 12.977500, 77.620800, TRUE,  UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0);
