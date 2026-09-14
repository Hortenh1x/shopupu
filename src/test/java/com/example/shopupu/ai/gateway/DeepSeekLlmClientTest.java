package com.example.shopupu.ai.gateway;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.example.shopupu.ai.guard.AiUsageGuard;
import com.example.shopupu.config.AiProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DeepSeekLlmClientTest {
    private final AiProperties properties = new AiProperties();
    private final RestClient.Builder builder = RestClient.builder();
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    @Test
    void disabledNeverReachesHttpEvenWithLiveProviderSelected() {
        assertTrue(client().parseCatalogQuery("shirt").isEmpty());
        server.verify();
    }

    @Test
    void sendsBoundedNonThinkingRequestAndDegradesOnTimeout() {
        properties.setEnabled(true);
        properties.setLlmApiKey("synthetic-test-key");
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andExpect(jsonPath("$.model").value("deepseek-v4-flash"))
                .andExpect(jsonPath("$.thinking.type").value("disabled"))
                .andExpect(jsonPath("$.max_tokens").value(properties.getMaxOutputTokens()))
                .andRespond(withException(new IOException("synthetic timeout")));
        assertTrue(client().parseCatalogQuery("shirt").isEmpty());
        server.verify();
    }

    @Test
    void rejectsOversizedProviderOutputInsteadOfParsingIt() {
        properties.setEnabled(true);
        properties.setLlmApiKey("synthetic-test-key");
        properties.setMaxResponseBytes(1024);
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("x".repeat(1025), MediaType.APPLICATION_JSON));
        assertTrue(client().parseCatalogQuery("shirt").isEmpty());
        server.verify();
    }

    @Test
    void acceptsValidJsonWithNormalProviderMetadata() {
        properties.setEnabled(true);
        properties.setLlmApiKey("synthetic-test-key");
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
                .andRespond(withSuccess("""
                        {"id":"synthetic-response","choices":[{"message":{"role":"assistant",
                        "content":"{\\"q\\":\\"shirt\\",\\"maxPrice\\":25}","reasoning_content":null}}]}
                        """, MediaType.APPLICATION_JSON));
        var result = client().parseCatalogQuery("shirt under 25");
        assertTrue(result.isPresent());
        assertEquals("shirt", result.orElseThrow().q());
        server.verify();
    }

    private DeepSeekLlmClient client() {
        return new DeepSeekLlmClient(properties, new ObjectMapper(), new AiUsageGuard(properties), builder);
    }

    @Test
    void stylistPromptStatesTheRequestLocaleAsTheDefaultLanguage() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.GERMAN);
        try {
            assertTrue(DeepSeekLlmClient.interfaceLanguageHint().contains("in German;"));
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
        // No request context (background work) and unsupported locales fall back to English.
        assertTrue(DeepSeekLlmClient.interfaceLanguageHint().contains("in English;"));
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.FRENCH);
        try {
            assertTrue(DeepSeekLlmClient.interfaceLanguageHint().contains("in English;"));
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }
}
