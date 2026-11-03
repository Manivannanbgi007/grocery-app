package com.grocery.delivery.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the saved proof-of-delivery photos as plain files under /delivery-media/**.
 */
@Configuration
@RequiredArgsConstructor
public class MediaConfig implements WebMvcConfigurer {

    private final MediaProperties mediaProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/delivery-media/**")
                .addResourceLocations(mediaProperties.uploadDir().toAbsolutePath().toUri().toString());
    }
}
