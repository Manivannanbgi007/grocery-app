package com.grocery.gateway.exception;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Settings under "app.fallback" in application.yml.
 *
 * @param emptyListPaths GET path patterns whose data is only decoration (e.g. /api/v1/banners): when their service
 *                       cannot answer, GlobalErrorHandler answers an empty list (200) instead of 503 / 504
 */
@ConfigurationProperties(prefix = "app.fallback")
public record FallbackProperties(List<String> emptyListPaths) {

    public FallbackProperties {
        emptyListPaths = emptyListPaths == null ? List.of() : List.copyOf(emptyListPaths);
    }
}
