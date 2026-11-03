package com.grocery.product.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves the uploaded product images: GET /media/products/12/abc.jpg reads
 * {uploadDir}/products/12/abc.jpg from disk.
 *
 * @EnableConfigurationProperties makes MediaProperties available wherever this class is loaded,
 * including web-layer tests (@WebMvcTest), which load WebMvcConfigurers but not the property scan.
 */
@Configuration
@EnableConfigurationProperties(MediaProperties.class)
@RequiredArgsConstructor
public class MediaConfig implements WebMvcConfigurer {

    private final MediaProperties mediaProperties;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/media/**")
                .addResourceLocations(mediaProperties.uploadDir().toAbsolutePath().toUri().toString());
    }
}
