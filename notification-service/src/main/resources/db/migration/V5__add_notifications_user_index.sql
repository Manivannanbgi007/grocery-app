-- "My notifications", newest first: idx_notifications_user_read (user_id, is_read) found the user's rows but then
-- sorted ALL of them for every page ("Using filesort"). (user_id, id) reads them in order and stops after one page.
-- The old index stays for the unread count. Found with EXPLAIN (docs/resilience.md, "Database").
CREATE INDEX idx_notifications_user ON notifications (user_id, id);
