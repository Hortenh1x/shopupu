package com.example.shopupu.config;

import com.example.shopupu.common.i18n.LocalizedMessages;
import com.example.shopupu.common.i18n.SupportedLocales;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class LocaleConfiguration {
    @Bean
    public LocaleResolver localeResolver() {
        return new AcceptHeaderLocaleResolver() {
            @Override public Locale resolveLocale(HttpServletRequest request) {
                return SupportedLocales.fromHeader(request.getHeader("Accept-Language"));
            }
        };
    }

    @Bean
    public MessageSource messageSource() {
        return LocalizedMessages.source();
    }
}
