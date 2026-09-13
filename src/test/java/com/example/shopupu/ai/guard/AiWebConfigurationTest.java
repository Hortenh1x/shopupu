package com.example.shopupu.ai.guard;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.shopupu.ai.controller.StylistController;
import com.example.shopupu.ai.dto.StylistChatResponse;
import com.example.shopupu.ai.service.StylistService;
import com.example.shopupu.config.AiProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AiWebConfigurationTest {
    @Test
    void stylistQuotaReturnsProblemDetailBeforeCallingServiceAndIgnoresSpoofedForwarding() throws Exception {
        AiProperties properties = new AiProperties();
        properties.setRequestsPerMinute(1);
        StylistService service = mock(StylistService.class);
        when(service.chat(any())).thenReturn(new StylistChatResponse("Offline", List.of(), List.of(), true));
        var mvc = MockMvcBuilders.standaloneSetup(new StylistController(service))
                .addInterceptors(new AiWebConfiguration(new AiRequestLimiter(properties)))
                .setControllerAdvice(new AiExceptionHandler()).build();
        MDC.put("requestId", "synthetic-ai-request");
        try {
            mvc.perform(post("/api/v1/catalog/stylist/chat").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"shirt\"}").header("X-Forwarded-For", "192.0.2.10"))
                    .andExpect(status().isOk());
            mvc.perform(post("/api/v1/catalog/stylist/chat").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"message\":\"shirt\"}").header("X-Forwarded-For", "192.0.2.11"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.code").value("AI_RATE_LIMITED"))
                    .andExpect(jsonPath("$.requestId").value("synthetic-ai-request"));
            verify(service, times(1)).chat(any());
        } finally {
            MDC.remove("requestId");
        }
    }
}
