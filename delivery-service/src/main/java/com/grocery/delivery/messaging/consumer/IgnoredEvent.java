package com.grocery.delivery.messaging.consumer;

/**
 * Stands in for an event type this service does not handle (it has no generated class for it).
 * See {@link KnownEventsAvroDeserializer}.
 *
 * @param eventType full Avro name, e.g. com.grocery.events.store.StoreRegistered
 */
public record IgnoredEvent(String eventType) {
}
