package com.example.shopupu.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Kept off the application class so that test slices (@DataJpaTest, @WebMvcTest)
 * do not require a CacheManager/scheduler to load.
 */
@Configuration
@EnableCaching
@EnableScheduling
@EnableJpaAuditing
public class InfrastructureConfig {
}
