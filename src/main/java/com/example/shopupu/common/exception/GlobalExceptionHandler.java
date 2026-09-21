package com.example.shopupu.common.exception;

import com.example.shopupu.common.i18n.LocalizedMessages;
import com.example.shopupu.common.i18n.SupportedLocales;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ServiceUnavailableException.class)
    public ProblemDetail handleUnavailable(ServiceUnavailableException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), ex.getCode(), request);
    }

    @ExceptionHandler(com.example.shopupu.auth.service.AuthRateLimitException.class)
    public ProblemDetail handleAuthRateLimit(com.example.shopupu.auth.service.AuthRateLimitException ex,
            HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response) {
        response.setHeader("Retry-After", Long.toString(ex.retryAfterSeconds()));
        return baseProblem(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), "AUTH_RATE_LIMITED", request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = baseProblem(HttpStatus.BAD_REQUEST, "validation failed", "VALIDATION_FAILED", request);
        List<Map<String, String>> errors = new ArrayList<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.add(Map.of("field", error.getField(), "message", LocalizedMessages.fieldError(error,
                    SupportedLocales.fromHeader(request.getHeader("Accept-Language")))));
        }
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", request);
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ProblemDetail handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.NOT_FOUND, ex.getMessage(), "NOT_FOUND", request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.NOT_FOUND, "Resource not found", "NOT_FOUND", request);
    }

    @ExceptionHandler(ConflictException.class)
    public ProblemDetail handleConflict(ConflictException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.CONFLICT, ex.getMessage(), "CONFLICT", request);
    }

    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage(), "BUSINESS_RULE_VIOLATION", request);
    }

    @ExceptionHandler({UnauthorizedException.class, AuthenticationException.class})
    public ProblemDetail handleUnauthorized(RuntimeException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.UNAUTHORIZED, ex.getMessage(), "UNAUTHORIZED", request);
    }

    @ExceptionHandler({ForbiddenOperationException.class, AccessDeniedException.class, SecurityException.class})
    public ProblemDetail handleForbidden(RuntimeException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.FORBIDDEN, ex.getMessage(), "FORBIDDEN", request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.BAD_REQUEST, "Malformed request body", "BAD_REQUEST", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.BAD_REQUEST,
                "Invalid value for parameter '" + ex.getName() + "'", "BAD_REQUEST", request);
    }

    // A wrong verb or content type on a known path is the client's mistake; it used to reach the
    // catch-all as a 500 with an error log for every probe.
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed for this resource", "METHOD_NOT_ALLOWED", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported request content type", "UNSUPPORTED_MEDIA_TYPE", request);
    }

    // Tomcat rejects the body before the controller runs; a client-side mistake, not a server fault.
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.PAYLOAD_TOO_LARGE, "Upload exceeds the size limit", "PAYLOAD_TOO_LARGE", request);
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ProblemDetail handleLegacy(RuntimeException ex, HttpServletRequest request) {
        return baseProblem(HttpStatus.BAD_REQUEST, ex.getMessage(), "BAD_REQUEST", request);
    }

    // Catch-all: never leak internals; log with the request id for correlation (ERR-05, SEC-15).
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception for {} {} (requestId={})",
                request.getMethod(), request.getRequestURI(), MDC.get("requestId"), ex);
        return baseProblem(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred", "INTERNAL_ERROR", request);
    }

    private ProblemDetail baseProblem(HttpStatus status, String detail, String code, HttpServletRequest request) {
        var locale = SupportedLocales.fromHeader(request.getHeader("Accept-Language"));
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, LocalizedMessages.detail(code, detail, locale));
        problem.setTitle(LocalizedMessages.title(status, locale));
        problem.setType(URI.create("urn:shopupu:error:" + code.toLowerCase(java.util.Locale.ROOT).replace('_', '-')));
        problem.setProperty("locale", locale.getLanguage());
        problem.setProperty("code", code);
        problem.setInstance(URI.create(request.getRequestURI()));
        String requestId = MDC.get("requestId");
        if (requestId != null) {
            problem.setProperty("requestId", requestId);
        }
        if (status.is4xxClientError()) {
            log.warn("{} {} -> {} {}", request.getMethod(), request.getRequestURI(), status.value(), detail);
        }
        return problem;
    }

}
