-- HousekeepingJob deletes processed_event rows by age (WHERE processed_at < ?): without this index every run would
-- read the whole table.
CREATE INDEX idx_processed_event_processed_at ON processed_event (processed_at);
