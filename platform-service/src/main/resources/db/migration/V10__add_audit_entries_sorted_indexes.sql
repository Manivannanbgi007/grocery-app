-- The audit log grows fastest of all tables (one row per event) and is listed newest first (occurred_at DESC).
-- A filter by store had no index at all, and the filters by event type or user sorted all matching rows for every
-- page. Each index now ends with occurred_at, so a filtered page is read in order. The new indexes start with the
-- same column as the old single-column ones, so those are replaced. Found with EXPLAIN (docs/resilience.md, "Database").
CREATE INDEX idx_audit_entries_store_occurred ON audit_entries (store_id, occurred_at);
CREATE INDEX idx_audit_entries_type_occurred ON audit_entries (event_type, occurred_at);
CREATE INDEX idx_audit_entries_user_occurred ON audit_entries (user_id, occurred_at);
DROP INDEX idx_audit_entries_event_type ON audit_entries;
DROP INDEX idx_audit_entries_user_id ON audit_entries;
