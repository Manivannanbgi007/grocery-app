-- A store's orders, newest first, without a status filter (GET /api/v1/vendor/orders): idx_orders_store_status
-- (store_id, status) cannot give them in id order, so MySQL walked ALL orders backwards looking for the store's.
-- With (store_id, id) it reads exactly the 20 rows of the page. Found with EXPLAIN (docs/resilience.md, "Database").
CREATE INDEX idx_orders_store_id ON orders (store_id, id);
