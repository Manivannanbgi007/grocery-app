package com.grocery.customer.messaging.consumer;

import com.grocery.customer.constant.KafkaTopics;
import com.grocery.customer.service.UserEventService;
import com.grocery.events.user.UserRegistered;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.user.events. Kept thin: it only hands each event to the service layer.
 * Spring calls the @KafkaHandler method whose parameter type matches the event.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.USER_EVENTS)
public class UserEventsConsumer {

    private final UserEventService userEventService;

    @KafkaHandler
    public void onUserRegistered(UserRegistered event) {
        log.info("Received UserRegistered eventId={} userId={} role={}", event.getEventId(), event.getUserId(), event.getRole());
        userEventService.handleUserRegistered(event);
    }

    /** User events customer-service does not need (PasswordResetRequested ...). */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
