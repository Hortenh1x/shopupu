package com.example.shopupu.ai.guard;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class AiWebConfiguration implements WebMvcConfigurer, HandlerInterceptor {
    private final AiRequestLimiter limiter;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method
                && method.getBeanType().getPackageName().equals("com.example.shopupu.ai.controller")) {
            // Security has already run. Never trust raw forwarding headers or a supplied account id.
            String peer = request.getRemoteAddr();
            var principal = request.getUserPrincipal();
            limiter.check(peer == null ? "unknown" : peer, principal == null ? null : principal.getName());
        }
        return true;
    }
}
