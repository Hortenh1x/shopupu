package com.example.shopupu.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.shopupu.common.web.CorrelationIdFilter;
import com.example.shopupu.common.web.SecurityProblemWriter;
import com.example.shopupu.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {
    @Test
    void forgedForwardingHeaderDoesNotMintANewBucketAndProblemKeepsRequestId() throws Exception {
        var properties = new RateLimitProperties();
        properties.setAuthCapacity(1);
        properties.setAuthRefillPerMinute(1);
        var mapper = new ObjectMapper();
        var limiter = new RateLimitFilter(properties, new SecurityProblemWriter(mapper));
        var correlation = new CorrelationIdFilter();
        for (int i = 0; i < 2; i++) {
            var request = new MockHttpServletRequest("POST", "/api/v1/auth/login");
            request.setRemoteAddr("192.0.2.1");
            request.addHeader("X-Forwarded-For", "198.51.100." + i);
            request.addHeader("X-Request-Id", "test-request-123");
            var response = new MockHttpServletResponse();
            correlation.doFilter(request, response, (req, res) -> limiter.doFilter(req, res, (ignoredReq, ignoredRes) -> {}));
            if (i == 0) assertThat(response.getStatus()).isEqualTo(200);
            else {
                assertThat(response.getStatus()).isEqualTo(429);
                assertThat(response.getHeader("Retry-After")).isNotBlank();
                var body = mapper.readTree(response.getContentAsString());
                assertThat(body.path("code").asText()).isEqualTo("RATE_LIMITED");
                assertThat(body.path("requestId").asText()).isEqualTo(response.getHeader("X-Request-Id")).isEqualTo("test-request-123");
                assertThat(body.path("instance").asText()).isEqualTo("/api/v1/auth/login");
            }
        }
    }
}
