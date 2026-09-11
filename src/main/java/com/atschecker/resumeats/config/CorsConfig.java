package com.atschecker.resumeats.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the Chrome extension (origin: chrome-extension://<id>) and local
 * development tools to call the backend REST API.
 *
 * NOTE: "*" is used for allowedOriginPatterns here because a Chrome
 * extension's origin includes its randomly generated ID, which you won't
 * know until you load the unpacked extension. For a production deployment
 * you can tighten this to your exact extension ID:
 * "chrome-extension://your-actual-extension-id"
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(false)
                .maxAge(3600);
    }
}
