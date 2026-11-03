package com.grocery.store.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The clock used for "is the store open now?", in the platform's time zone.
 * Code asks this bean for the time instead of calling LocalDateTime.now() directly,
 * so tests can use a fixed clock.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(StoreProperties storeProperties) {
        return Clock.system(storeProperties.timeZone());
    }
}
