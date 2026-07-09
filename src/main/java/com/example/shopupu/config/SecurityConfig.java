package com.example.shopupu.config;

import com.example.shopupu.security.JwtAuthFilter;
import com.example.shopupu.security.RateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.HeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    // Strict CSP for the API (SEC-04). Browsers don't render JSON, so 'none' is safe there.
    private static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    // Swagger UI serves its own JS/CSS and fetches /v3/api-docs — 'none' would blank it out.
    // Only the docs paths get this relaxed policy (see cspHeaderWriter).
    private static final String DOCS_CSP =
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'; "
                    + "img-src 'self' data:; font-src 'self'; connect-src 'self'; "
                    + "frame-ancestors 'none'; base-uri 'none'";

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                // Wire CORS into the security chain so the CorsFilter runs before
                // authorization and uses the CorsConfigurationSource bean (CorsConfig).
                // Without this, deny-by-default rejects browser cross-origin requests.
                .cors(Customizer.withDefaults())
                // CSRF is intentionally disabled: stateless JWT API, tokens travel in the
                // Authorization header only, no cookie-based sessions (SEC-03).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .headers(headers -> headers
                        // one CSP header, chosen per path: strict on the API, Swagger-friendly on the docs
                        .addHeaderWriter(cspHeaderWriter())
                        .referrerPolicy(rp -> rp.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000))
                )
                .authorizeHttpRequests(auth -> auth
                        // deny-by-default: everything below is an explicit whitelist,
                        // anything not listed requires authentication
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh",
                                "/api/v1/auth/forgot-password", "/api/v1/auth/reset-password",
                                "/api/v1/auth/verify-email").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalog/**").permitAll()
                        // guest carts are scoped by an opaque X-Cart-Token (CART-01)
                        .requestMatchers("/api/v1/cart/**").permitAll()
                        // payment callbacks are authenticated by provider signature, not JWT
                        .requestMatchers(HttpMethod.POST, "/api/v1/payments/callback").permitAll()
                        .requestMatchers("/swagger", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/health").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/users/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/admin/**").hasAnyRole("ADMIN", "MANAGER")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, e) ->
                                writeProblem(response, 401, "Authentication required"))
                        .accessDeniedHandler((request, response, e) ->
                                writeProblem(response, 403, "Access denied")))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(rateLimitFilter, JwtAuthFilter.class)
                .build();
    }

    /**
     * Emits exactly one Content-Security-Policy header, chosen by path: the relaxed
     * DOCS_CSP for Swagger/OpenAPI so its bundled JS/CSS and /v3/api-docs fetch work,
     * the strict API_CSP everywhere else. A single header avoids the intersection of
     * two CSPs (which would re-block Swagger).
     */
    private static HeaderWriter cspHeaderWriter() {
        return (request, response) -> {
            String uri = request.getRequestURI();
            boolean docs = uri.startsWith("/swagger") || uri.startsWith("/v3/api-docs");
            response.setHeader("Content-Security-Policy", docs ? DOCS_CSP : API_CSP);
        };
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse response, int status, String detail)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.getWriter().write("{\"type\":\"urn:shopupu:error:"
                + (status == 401 ? "unauthorized" : "forbidden")
                + "\",\"title\":\"" + (status == 401 ? "unauthorized" : "forbidden")
                + "\",\"status\":" + status + ",\"detail\":\"" + detail + "\"}");
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
