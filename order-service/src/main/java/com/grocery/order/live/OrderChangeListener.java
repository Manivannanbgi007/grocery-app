package com.grocery.order.live;

import com.grocery.order.constant.KafkaTopics;
import com.grocery.order.dto.response.OrderChange;
import com.grocery.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Tells the vendors' live streams on THIS instance (VendorOrderStreams) that an order of their store changed.
 *
 * Every change of an order is already an event on grocery.order.events (outbox + Debezium), with the order id as the
 * message key. This listener reads that topic:
 *   - with a consumer group of its own per instance: a vendor's stream is open on ONE instance, and a shared group
 *     would give each event to only one instance. With its own group, every instance sees every event and keeps the
 *     ones for its own streams.
 *   - from the newest event on (auto.offset.reset=latest): a live stream needs what happens from now on. (The
 *     service's default, earliest, would replay the whole order history at every start.)
 *   - without saving its place (ackMode MANUAL, and nothing is ever acknowledged): it starts at the newest event
 *     anyway, and a group without saved offsets disappears from Kafka a few minutes after its instance stopped. A
 *     saved place would stay behind for days, with a growing lag that the KafkaConsumerNoProgress alert reports.
 *   - without reading the event itself (Avro, so the value is just bytes here): the key says which order, and the
 *     database has its store and status (the outbox row was saved in the same transaction as the change).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderChangeListener {

    /** The listener's name in Spring (not its consumer group, which is groupId below). */
    public static final String LISTENER_ID = "live-order-changes";

    private final VendorOrderStreams vendorOrderStreams;
    private final OrderRepository orderRepository;

    @KafkaListener(
            id = LISTENER_ID,
            topics = KafkaTopics.ORDER_EVENTS,
            groupId = "${spring.application.name}-live-${random.uuid}",
            ackMode = "MANUAL",
            properties = {
                    "auto.offset.reset=latest",
                    "value.deserializer=org.apache.kafka.common.serialization.ByteArrayDeserializer"})
    public void onOrderEvent(ConsumerRecord<String, byte[]> event) {
        if (!vendorOrderStreams.hasOpenStreams() || event.key() == null) {
            return;   // nobody follows their orders on this instance: no database read
        }
        try {
            orderRepository.findById(Long.valueOf(event.key())).ifPresent(order ->
                    vendorOrderStreams.sendOrderChange(order.getStoreId(), new OrderChange(order.getId(), order.getStatus())));
        } catch (RuntimeException ex) {
            // A live update is a nice-to-have (the page also checks once a minute): never retried, never a dead letter
            log.warn("No live update for the order event with key {}: {}", event.key(), ex.getMessage());
        }
    }
}
