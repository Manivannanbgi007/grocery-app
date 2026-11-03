-- The trace of the request or event that created the event, as a W3C traceparent value
-- (00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01). Debezium sends it as the Kafka header "traceparent",
-- so the consumer's work belongs to the same trace in Jaeger. Empty for events created outside a trace.
ALTER TABLE outbox_event ADD COLUMN traceparent VARCHAR(55) NULL;
