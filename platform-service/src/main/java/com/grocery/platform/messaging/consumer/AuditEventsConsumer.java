package com.grocery.platform.messaging.consumer;

import com.grocery.platform.constant.KafkaTopics;
import com.grocery.platform.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Reads EVERY domain topic for the audit log. The events arrive as GenericRecord (specific.avro.reader: false),
 * so new event types are audited too without any code change here.
 */
@Component
@RequiredArgsConstructor
public class AuditEventsConsumer {

    private final AuditLogService auditLogService;

    @KafkaListener(topics = {KafkaTopics.USER_EVENTS, KafkaTopics.STORE_EVENTS, KafkaTopics.INVENTORY_EVENTS,
            KafkaTopics.ORDER_EVENTS, KafkaTopics.PAYMENT_EVENTS, KafkaTopics.DELIVERY_EVENTS})
    public void onEvent(ConsumerRecord<String, GenericRecord> record) {
        auditLogService.recordEvent(record.topic(), record.key(), record.value());
    }
}
