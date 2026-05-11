package com.example.shopupu.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
/**
 * describes the JacksonCompatibilityConfig class.
 */
public class JacksonCompatibilityConfig {

    @Bean
    // handles legacyObjectMapper.
    public ObjectMapper legacyObjectMapper() {
        return new ObjectMapper().registerModule(new JavaTimeModule());
    }
}
