package com.example.shopupu.common.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shopupu.common.exception.BadRequestException;
import com.example.shopupu.common.exception.GlobalExceptionHandler;
import com.example.shopupu.common.web.SecurityProblemWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class LocalizationTest {
    @Test void languageNegotiationSupportsRegionalTagsAndQualityWithEnglishFallback() {
        assertThat(SupportedLocales.fromHeader("de-DE,de;q=0.9,en;q=0.8")).isEqualTo(Locale.GERMAN);
        assertThat(SupportedLocales.fromHeader("de;q=0.1,en-US;q=0.9")).isEqualTo(Locale.ENGLISH);
        assertThat(SupportedLocales.fromHeader("uk,fr;q=0.8")).isEqualTo(Locale.ENGLISH);
        assertThat(SupportedLocales.fromHeader("not a header")).isEqualTo(Locale.ENGLISH);
        assertThat(SupportedLocales.fromHeader(null)).isEqualTo(Locale.ENGLISH);
        assertThat(SupportedLocales.languageTags()).containsExactly("en", "de");
    }

    @Test void mvcErrorsPreserveMachineCodeAndTranslateKnownAndUnknownDetails() {
        var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        request.addHeader("Accept-Language", "de-DE");
        var handler = new GlobalExceptionHandler();
        var known = handler.handleBadRequest(new BadRequestException("Current password is incorrect"), request);
        assertThat(known.getProperties()).containsEntry("locale", "de").containsEntry("code", "BAD_REQUEST");
        assertThat(known.getDetail()).isEqualTo("Das aktuelle Passwort ist falsch.");
        var unknown = handler.handleBadRequest(new BadRequestException("Untranslated internal legacy detail"), request);
        assertThat(unknown.getDetail()).isEqualTo("Die Anfrage ist ungültig. Bitte prüfe deine Eingaben.");
    }

    @Test void securityErrorsUseHeaderBeforeMvcWithoutLosingCorrelation() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/v1/orders");
        request.addHeader("Accept-Language", "de");
        var response = new MockHttpServletResponse();
        new SecurityProblemWriter(new ObjectMapper()).write(request, response, org.springframework.http.HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication required");
        var body = new ObjectMapper().readTree(response.getContentAsString());
        assertThat(body.path("locale").asText()).isEqualTo("de");
        assertThat(body.path("detail").asText()).isEqualTo("Bitte melde dich an.");
        assertThat(body.path("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(body.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id")).isNotBlank();
    }
}
