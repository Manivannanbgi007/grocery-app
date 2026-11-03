package com.grocery.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under "app.order" in application.yml.
 *
 * @param requireStoreOpen true = orders are only accepted while the store is open.
 *                         The local profile switches it off, so you can test at any time of day.
 */
@ConfigurationProperties(prefix = "app.order")
public record OrderProperties(boolean requireStoreOpen) {
}
