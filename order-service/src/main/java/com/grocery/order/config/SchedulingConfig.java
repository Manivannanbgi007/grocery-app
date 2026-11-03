package com.grocery.order.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled methods (HousekeepingJob).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
