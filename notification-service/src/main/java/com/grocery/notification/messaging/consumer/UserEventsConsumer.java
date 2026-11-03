package com.grocery.notification.messaging.consumer;

import com.grocery.events.user.PasswordResetRequested;
import com.grocery.events.user.SocialLoginLinked;
import com.grocery.events.user.UserRegistered;
import com.grocery.notification.constant.KafkaTopics;
import com.grocery.notification.service.EventNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.user.events and hands the events that need a notification to the service layer.
 * One listener class per topic (own consumer thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.USER_EVENTS)
public class UserEventsConsumer {

    private final EventNotificationService eventNotificationService;

    @KafkaHandler
    public void onUserRegistered(UserRegistered event) {
        log.info("Received UserRegistered eventId={} userId={}", event.getEventId(), event.getUserId());
        eventNotificationService.handleUserRegistered(event);
    }

    @KafkaHandler
    public void onPasswordResetRequested(PasswordResetRequested event) {
        log.info("Received PasswordResetRequested eventId={} userId={}", event.getEventId(), event.getUserId());
        eventNotificationService.handlePasswordResetRequested(event);
    }

    @KafkaHandler
    public void onSocialLoginLinked(SocialLoginLinked event) {
        log.info("Received SocialLoginLinked eventId={} userId={}", event.getEventId(), event.getUserId());
        eventNotificationService.handleSocialLoginLinked(event);
    }

    /** Events of this topic that need no notification. */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
