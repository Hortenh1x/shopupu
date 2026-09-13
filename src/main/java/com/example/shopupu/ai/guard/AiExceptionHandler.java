package com.example.shopupu.ai.guard;

import com.example.shopupu.common.i18n.LocalizedMessages;
import com.example.shopupu.common.i18n.SupportedLocales;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** AI-specific status; the shared handler continues to own all ordinary domain errors. */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(basePackages = "com.example.shopupu.ai.controller")
public class AiExceptionHandler {
    @ExceptionHandler(AiRateLimitException.class)
    public ResponseEntity<ProblemDetail> rateLimited(AiRateLimitException exception, HttpServletRequest request) {
        var locale = SupportedLocales.fromHeader(request.getHeader("Accept-Language"));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                LocalizedMessages.detail("AI_RATE_LIMITED", exception.getMessage(), locale));
        problem.setTitle(LocalizedMessages.title(HttpStatus.TOO_MANY_REQUESTS, locale));
        problem.setProperty("locale", locale.getLanguage());
        problem.setType(URI.create("urn:shopupu:error:ai-rate-limited"));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", "AI_RATE_LIMITED");
        problem.setProperty("requestId", MDC.get("requestId"));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", Long.toString(exception.retryAfterSeconds())).body(problem);
    }
}
