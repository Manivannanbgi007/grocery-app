package com.grocery.mcpserver.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Switches on @Cacheable. The cache itself (Caffeine, entries kept 1 minute) is set in application.yml
 * under spring.cache; the cache names are in {@link CacheNames}.
 */
@Configuration
@EnableCaching
public class CacheConfig {
}
