package com.swapit.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(
                        "http://localhost:*",
                        "http://127.0.0.1:*",
                        "http://172.*.*.*:*",
                        "http://192.168.*.*:*",
                        "http://10.*.*.*:*",
                        "https://localhost:*",
                        "https://127.0.0.1:*",
                        "https://172.*.*.*:*",
                        "https://192.168.*.*:*",
                        "https://10.*.*.*:*",
                        "https://swapit.my",
                        "https://www.swapit.my",
                        "https://crew.swapit.my",
                        "https://*.swapit.my",
                        "https://*.vercel.app"
                )
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }
}
