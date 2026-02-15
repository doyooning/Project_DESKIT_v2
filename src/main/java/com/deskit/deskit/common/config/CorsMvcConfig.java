package com.deskit.deskit.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Configuration
public class CorsMvcConfig implements WebMvcConfigurer {

    private final List<String> allowedOrigins;

    public CorsMvcConfig(@Value("${app.cors.allowed-origins:http://localhost:5173}") String allowedOriginsRaw) {
        this.allowedOrigins = Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .collect(Collectors.toList());
    }

    @Override
    public void addCorsMappings(CorsRegistry corsRegistry) {

        corsRegistry.addMapping("/**")
                .exposedHeaders("Set-Cookie", "Authorization", "access")
                .allowedHeaders("Authorization", "Content-Type", "access")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedOrigins(allowedOrigins.toArray(String[]::new));
    }
}
