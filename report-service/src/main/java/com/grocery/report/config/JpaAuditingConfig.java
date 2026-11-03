package com.grocery.report.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Turns on automatic createdAt / updatedAt values (see BaseEntity).
 * Kept in its own class (not on the main application class) so web-layer tests can start without JPA.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
