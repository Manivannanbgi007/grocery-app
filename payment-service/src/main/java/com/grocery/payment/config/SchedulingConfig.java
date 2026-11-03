package com.grocery.payment.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled methods (PaymentTimeoutJob, HousekeepingJob).
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
