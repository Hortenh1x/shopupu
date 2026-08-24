package com.example.shopupu.config;

import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires "Sign in with Google" only when {@code google.client-id} is set, so the
 * feature stays inert until the operator pastes the client id (the frontend
 * hides the button in the same way). The verifier validates the ID token's
 * signature against Google's rotating public keys, plus issuer, audience, and
 * expiry — so a forged or wrong-audience token is rejected.
 */
@Configuration
public class GoogleAuthConfig {

    @Bean
    @ConditionalOnExpression("'${google.client-id:}' != ''")
    public GoogleIdTokenVerifier googleIdTokenVerifier(@Value("${google.client-id}") String clientId) {
        return new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), GsonFactory.getDefaultInstance())
                .setAudience(List.of(clientId))
                .build();
    }
}
