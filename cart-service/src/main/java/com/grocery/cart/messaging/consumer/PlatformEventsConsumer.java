package com.grocery.cart.messaging.consumer;

import com.grocery.cart.constant.KafkaTopics;
import com.grocery.cart.service.PricingSettingsService;
import com.grocery.events.platform.PlatformSettingsChanged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Listens to grocery.platform.events: the super admin changed the delivery fee or the minimum order.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@KafkaListener(topics = KafkaTopics.PLATFORM_EVENTS)
public class PlatformEventsConsumer {

    private final PricingSettingsService pricingSettingsService;

    @KafkaHandler
    public void onPlatformSettingsChanged(PlatformSettingsChanged event) {
        log.info("Received PlatformSettingsChanged eventId={}", event.getEventId());
        pricingSettingsService.applyPlatformSettings(event);
    }

    @KafkaHandler(isDefault = true)
    public void ignore(Object event) {
        log.debug("Ignoring {}", event);
    }
}
