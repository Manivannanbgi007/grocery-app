package com.grocery.mcpclient.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled jobs (ConversationCleanupJob).
 * Kept in its own class (not on the main application class) so web-layer tests start without it.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
