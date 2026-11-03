package com.grocery.platform.config;

import com.grocery.platform.context.UserContextHolder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/**
 * Turns on the automatic audit columns of BaseEntity: createdAt / updatedAt (when)
 * and createdBy / updatedBy (who, from the auditorAware bean below).
 * Kept in its own class (not on the main application class) so web-layer tests can start without JPA.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /**
     * Tells Spring Data who is saving a row. Never empty: with an empty value Spring Data would keep
     * the previous person in updatedBy when a Kafka event or a scheduled job changes the row.
     */
    @Bean
    public AuditorAware<String> auditorAware() {
        return () -> Optional.of(UserContextHolder.currentAuditor());
    }
}
