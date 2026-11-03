-- =====================================================================
-- Runs once, the first time the MySQL container starts (empty volume).
--
-- Database-per-Service pattern: every microservice gets its OWN database
-- and its OWN user. A user is granted access to its own database only,
-- so a service physically cannot read another service's tables.
--
-- Passwords are for local development only.
-- To re-run this script: scripts\reset-infra.ps1 (deletes all data).
-- =====================================================================

-- auth-service: users, roles, credentials
CREATE DATABASE IF NOT EXISTS auth_db;
CREATE USER IF NOT EXISTS 'auth_user'@'%' IDENTIFIED BY 'auth_pass';
GRANT ALL PRIVILEGES ON auth_db.* TO 'auth_user'@'%';

-- customer-service: customer profiles and addresses
CREATE DATABASE IF NOT EXISTS customer_db;
CREATE USER IF NOT EXISTS 'customer_user'@'%' IDENTIFIED BY 'customer_pass';
GRANT ALL PRIVILEGES ON customer_db.* TO 'customer_user'@'%';

-- store-service: stores, opening hours, delivery areas
CREATE DATABASE IF NOT EXISTS store_db;
CREATE USER IF NOT EXISTS 'store_user'@'%' IDENTIFIED BY 'store_pass';
GRANT ALL PRIVILEGES ON store_db.* TO 'store_user'@'%';

-- product-service: categories and products (write side of the catalog)
CREATE DATABASE IF NOT EXISTS product_db;
CREATE USER IF NOT EXISTS 'product_user'@'%' IDENTIFIED BY 'product_pass';
GRANT ALL PRIVILEGES ON product_db.* TO 'product_user'@'%';

-- inventory-service: stock and reservations
CREATE DATABASE IF NOT EXISTS inventory_db;
CREATE USER IF NOT EXISTS 'inventory_user'@'%' IDENTIFIED BY 'inventory_pass';
GRANT ALL PRIVILEGES ON inventory_db.* TO 'inventory_user'@'%';

-- cart-service: carts and coupons
CREATE DATABASE IF NOT EXISTS cart_db;
CREATE USER IF NOT EXISTS 'cart_user'@'%' IDENTIFIED BY 'cart_pass';
GRANT ALL PRIVILEGES ON cart_db.* TO 'cart_user'@'%';

-- order-service: orders and order status history
CREATE DATABASE IF NOT EXISTS order_db;
CREATE USER IF NOT EXISTS 'order_user'@'%' IDENTIFIED BY 'order_pass';
GRANT ALL PRIVILEGES ON order_db.* TO 'order_user'@'%';

-- payment-service: payments and refunds
CREATE DATABASE IF NOT EXISTS payment_db;
CREATE USER IF NOT EXISTS 'payment_user'@'%' IDENTIFIED BY 'payment_pass';
GRANT ALL PRIVILEGES ON payment_db.* TO 'payment_user'@'%';

-- delivery-service: delivery partners and assignments
CREATE DATABASE IF NOT EXISTS delivery_db;
CREATE USER IF NOT EXISTS 'delivery_user'@'%' IDENTIFIED BY 'delivery_pass';
GRANT ALL PRIVILEGES ON delivery_db.* TO 'delivery_user'@'%';

-- notification-service: notification log and in-app messages
CREATE DATABASE IF NOT EXISTS notification_db;
CREATE USER IF NOT EXISTS 'notification_user'@'%' IDENTIFIED BY 'notification_pass';
GRANT ALL PRIVILEGES ON notification_db.* TO 'notification_user'@'%';

-- report-service: dashboard and report read models
CREATE DATABASE IF NOT EXISTS report_db;
CREATE USER IF NOT EXISTS 'report_user'@'%' IDENTIFIED BY 'report_pass';
GRANT ALL PRIVILEGES ON report_db.* TO 'report_user'@'%';

-- platform-service: settings, feature flags, audit log
CREATE DATABASE IF NOT EXISTS platform_db;
CREATE USER IF NOT EXISTS 'platform_user'@'%' IDENTIFIED BY 'platform_pass';
GRANT ALL PRIVILEGES ON platform_db.* TO 'platform_user'@'%';

-- mcp-client-service: the AI assistant's conversation memory
CREATE DATABASE IF NOT EXISTS chat_db;
CREATE USER IF NOT EXISTS 'chat_user'@'%' IDENTIFIED BY 'chat_pass';
GRANT ALL PRIVILEGES ON chat_db.* TO 'chat_user'@'%';

-- Debezium (Kafka Connect) reads the binary log of every database.
-- These are the privileges listed in the Debezium MySQL connector docs.
CREATE USER IF NOT EXISTS 'debezium'@'%' IDENTIFIED BY 'debezium_pass';
GRANT SELECT, RELOAD, SHOW DATABASES, REPLICATION SLAVE, REPLICATION CLIENT, LOCK TABLES ON *.* TO 'debezium'@'%';

-- Prometheus MySQL exporter (docker-compose.yml, profile monitoring): monitoring rights only, no access to any data.
--   PROCESS                        -> connections and InnoDB statistics
--   REPLICATION CLIENT             -> binary log status
--   SELECT ON performance_schema.* -> statement and wait statistics
-- At most 3 connections, so a slow scrape can never use up the server's connections.
-- The password is MYSQL_EXPORTER_PASSWORD in infrastructure/.env.
CREATE USER IF NOT EXISTS 'exporter'@'%' IDENTIFIED BY 'exporter_pass' WITH MAX_USER_CONNECTIONS 3;
GRANT PROCESS, REPLICATION CLIENT ON *.* TO 'exporter'@'%';
GRANT SELECT ON performance_schema.* TO 'exporter'@'%';

FLUSH PRIVILEGES;
