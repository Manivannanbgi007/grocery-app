package com.grocery.gateway.config;

import com.grocery.gateway.constant.HeaderNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

import java.util.List;

/**
 * CORS: lets the React app (a different origin, e.g. http://localhost:5173) call the API from the browser.
 * Only the gateway handles CORS - the services behind it never see browser requests directly.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of(HeaderNames.CORRELATION_ID));   // let the frontend read it
        config.setMaxAge(3600L);                                           // browser caches pre-flight for 1 hour

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        // A social login provider's answer is a browser navigation, not a call from the React app. Apple's is a form POST
        // from https://appleid.apple.com: with the CORS check it would be refused (403). No CORS rules for it.
        PathPattern socialLoginCallback = PathPatternParser.defaultInstance.parse(SecurityConfig.SOCIAL_LOGIN_CALLBACK);
        return exchange -> socialLoginCallback.matches(exchange.getRequest().getPath().pathWithinApplication())
                ? null
                : source.getCorsConfiguration(exchange);
    }
}
