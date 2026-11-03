-- Admin search by status (GET /api/v1/admin/deliveries?status=...): no index started with status, so MySQL read
-- every delivery. (status, id) gives one status in id order. Found with EXPLAIN (docs/resilience.md, "Database").
CREATE INDEX idx_deliveries_status ON deliveries (status, id);
