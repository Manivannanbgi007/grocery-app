package com.grocery.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The current time comes from this Clock (not Instant.now()) wherever token lifetimes are decided, so tests can move
 * time forward instead of waiting 7 days.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
