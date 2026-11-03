package com.grocery.delivery.messaging.consumer;

import com.grocery.delivery.constant.KafkaTopics;
import com.grocery.delivery.service.DeliverySagaService;
import com.grocery.events.user.UserRegistered;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.user.events: new DELIVERY_PARTNER users get a partner profile.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.USER_EVENTS)
public class UserEventsConsumer {

    private final DeliverySagaService deliverySagaService;

    @KafkaHandler
    public void onUserRegistered(UserRegistered event) {
        log.info("Received UserRegistered eventId={} userId={} role={}", event.getEventId(), event.getUserId(), event.getRole());
        deliverySagaService.handleUserRegistered(event);
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
