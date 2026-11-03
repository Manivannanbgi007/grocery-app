-- Admin payments of one store (GET /api/v1/admin/payments?storeId=...): no index on store_id, so MySQL read every
-- payment. (store_id, id) gives a store's payments newest first. Found with EXPLAIN (docs/resilience.md, "Database").
CREATE INDEX idx_payments_store ON payments (store_id, id);
