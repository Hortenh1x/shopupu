package com.example.shopupu.config;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
/**
 * describes the StaticResourceConfig class.
 */
public class StaticResourceConfig implements WebMvcConfigurer {

    private final Path uploadsDir;

    // handles StaticResourceConfig.
    public StaticResourceConfig(@Value("${app.uploads.dir:uploads}") String uploadsDir) {
        this.uploadsDir = Path.of(uploadsDir).toAbsolutePath().normalize();
    }

    @Override
    // handles addResourceHandlers.
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(uploadsDir.toUri().toString());
    }
}
