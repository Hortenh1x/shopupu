package com.example.shopupu.common.web;

import com.example.shopupu.common.i18n.LocalizedMessages;
import com.example.shopupu.common.i18n.SupportedLocales;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

/** Problem Details for failures before MVC's exception advice is reached. */
@Component
@RequiredArgsConstructor
public class SecurityProblemWriter {
    private final ObjectMapper mapper;

    public void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
            String code, String detail) throws IOException {
        String requestId = MDC.get("requestId");
        if (requestId == null) requestId = UUID.randomUUID().toString();
        var body = new LinkedHashMap<String, Object>();
        body.put("type", "urn:shopupu:error:" + code.toLowerCase(Locale.ROOT).replace('_', '-'));
        var locale = SupportedLocales.fromHeader(request.getHeader("Accept-Language"));
        body.put("title", LocalizedMessages.title(status, locale));
        body.put("status", status.value());
        body.put("detail", LocalizedMessages.detail(code, detail, locale));
        body.put("locale", locale.getLanguage());
        body.put("instance", request.getRequestURI());
        body.put("code", code);
        body.put("requestId", requestId);
        response.setStatus(status.value());
        response.setHeader("Content-Language", locale.getLanguage());
        response.setHeader(CorrelationIdFilter.HEADER, requestId);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        mapper.writeValue(response.getWriter(), body);
    }
}
