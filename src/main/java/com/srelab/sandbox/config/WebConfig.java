package com.srelab.sandbox.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Allows the frontend dev server (Vite, default port 5173) and the eventual
 * Tauri-packaged app to call the REST/SSE API from a different origin than
 * the Spring Boot server itself. Scoped to localhost dev origins only --
 * this service is not intended to be exposed to the public internet.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("http://localhost:*", "tauri://localhost", "https://tauri.localhost")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*");
    }
}
