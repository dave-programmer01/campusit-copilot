package com.david.campusitcopilot.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Web MVC configuration for CORS mapping.
 * Allows browser requests from configured frontend origins (e.g. Vercel and localhost)
 * on student/client-facing endpoints (/chat, /deflection).
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(
            @Value("${app.cors.allowed-origin:http://localhost:3000,https://campusit-copilot.vercel.app}")
            String[] allowedOrigins) {
        this.allowedOrigins = Arrays.stream(allowedOrigins)
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/chat")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("POST", "GET", "OPTIONS")
                .allowedHeaders("Content-Type");

        registry.addMapping("/chat/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("POST", "GET", "OPTIONS")
                .allowedHeaders("Content-Type");

        registry.addMapping("/deflection")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("POST", "GET", "OPTIONS")
                .allowedHeaders("Content-Type");

        registry.addMapping("/deflection/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("POST", "GET", "OPTIONS")
                .allowedHeaders("Content-Type");

        registry.addMapping("/feedback")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("POST", "GET", "OPTIONS")
                .allowedHeaders("Content-Type");

        registry.addMapping("/feedback/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("POST", "GET", "OPTIONS")
                .allowedHeaders("Content-Type");
    }

    public String[] getAllowedOrigins() {
        return allowedOrigins;
    }
}
