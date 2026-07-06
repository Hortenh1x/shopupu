package com.example.shopupu.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * ETag-based HTTP caching for public catalog reads (CACHE-03): clients revalidate
 * with If-None-Match and get 304 when nothing changed.
 */
@Configuration
public class HttpCachingConfig {

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> catalogEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/api/v1/catalog/*", "/uploads/*");
        registration.setName("catalogEtagFilter");
        return registration;
    }
}
