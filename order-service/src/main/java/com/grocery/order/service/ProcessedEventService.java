package com.grocery.order.service;

/**
 * Makes event handling idempotent: the same Kafka event can be delivered more than once.
 */
public interface ProcessedEventService {

    /**
     * Records that an event is being handled.
     * Must be called inside the handler's transaction, so it is rolled back if the handling fails.
     *
     * @return true the first time an event id is seen, false for a duplicate (skip it)
     */
    boolean markAsProcessed(String eventId, String eventType);
}
