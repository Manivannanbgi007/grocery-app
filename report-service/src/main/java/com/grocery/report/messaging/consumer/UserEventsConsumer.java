package com.grocery.report.messaging.consumer;

import com.grocery.events.user.UserRegistered;
import com.grocery.report.constant.KafkaTopics;
import com.grocery.report.service.ReportProjectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.user.events. One listener class per topic (own consumer thread).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.USER_EVENTS)
public class UserEventsConsumer {

    private final ReportProjectionService projectionService;

    @KafkaHandler
    public void onUserRegistered(UserRegistered event) {
        projectionService.onUserRegistered(event);
    }

    /** Events of this topic the reports do not need. */
    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
