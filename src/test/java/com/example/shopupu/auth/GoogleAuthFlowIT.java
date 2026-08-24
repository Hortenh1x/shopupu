package com.example.shopupu.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.shopupu.identity.entity.AuthProvider;
import com.example.shopupu.identity.entity.User;
import com.example.shopupu.identity.repository.UserRepository;
import com.example.shopupu.support.PostgresContainerSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.json.webtoken.JsonWebSignature;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Deep pass over "Sign in with Google" (AUTH-13, V18) against the real security
 * chain and database: first-login provisioning, linking to an existing local
 * account by verified email, and the refresh-rotation flow with reuse detection.
 * The Google verifier is mocked — everything after token verification is real.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class GoogleAuthFlowIT extends PostgresContainerSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private GoogleIdTokenVerifier googleIdTokenVerifier;

    @Test
    void firstGoogleLoginProvisionsCustomerAndTokensWork() throws Exception {
        String email = "g-new-" + System.nanoTime() + "@example.com";
        when(googleIdTokenVerifier.verify("good-token")).thenReturn(googleToken(email, true));

        JsonNode pair = postGoogleLogin("good-token", status().isOk());

        User created = userRepository.findByEmail(email).orElseThrow();
        assertEquals(AuthProvider.GOOGLE, created.getAuthProvider());
        assertTrue(created.isEmailVerified(), "Google-asserted email must be trusted as verified");
        assertTrue(created.getRoles().stream().anyMatch(r -> "CUSTOMER".equals(r.getName())));

        // the issued access token must open a protected endpoint
        mockMvc.perform(get("/api/v1/users/me/profile")
                        .header("Authorization", "Bearer " + pair.get("accessToken").asText()))
                .andExpect(status().isOk());
    }

    @Test
    void googleLoginLinksToExistingLocalAccountWithoutDuplicating() throws Exception {
        String email = "g-link-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"Password123!","passwordConfirm":"Password123!"}
                                """.formatted(email)))
                .andExpect(status().isCreated());
        Long localId = userRepository.findByEmail(email).orElseThrow().getId();

        when(googleIdTokenVerifier.verify("link-token")).thenReturn(googleToken(email, true));
        postGoogleLogin("link-token", status().isOk());

        User linked = userRepository.findByEmail(email).orElseThrow();
        assertEquals(localId, linked.getId(), "Google login must reuse the existing account, not create a new one");
        assertEquals(AuthProvider.LOCAL, linked.getAuthProvider(),
                "linking must not flip the account's owning provider — password login keeps working");
    }

    @Test
    void googleRefreshRotationDetectsReuseAndRevokesChain() throws Exception {
        String email = "g-refresh-" + System.nanoTime() + "@example.com";
        when(googleIdTokenVerifier.verify("refresh-token-login")).thenReturn(googleToken(email, true));

        JsonNode first = postGoogleLogin("refresh-token-login", status().isOk());
        String refresh1 = first.get("refreshToken").asText();

        JsonNode second = postRefresh(refresh1, status().isOk());
        String refresh2 = second.get("refreshToken").asText();
        assertNotEquals(refresh1, refresh2, "refresh must rotate the token");

        // replaying the rotated-out token is reuse: rejected and the whole chain revoked
        postRefresh(refresh1, status().isUnauthorized());
        postRefresh(refresh2, status().isUnauthorized());
    }

    @Test
    void googleLoginWithUnverifiedEmailIsRejected() throws Exception {
        String email = "g-unverified-" + System.nanoTime() + "@example.com";
        when(googleIdTokenVerifier.verify("unverified-token")).thenReturn(googleToken(email, false));

        postGoogleLogin("unverified-token", status().isUnauthorized());
        assertTrue(userRepository.findByEmail(email).isEmpty(), "no account may be provisioned");
    }

    @Test
    void forgedGoogleTokenIsRejected() throws Exception {
        when(googleIdTokenVerifier.verify("forged-token")).thenReturn(null);

        postGoogleLogin("forged-token", status().isUnauthorized());
    }

    private JsonNode postGoogleLogin(String idToken, org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/google")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idToken\":\"" + idToken + "\"}"))
                .andExpect(expected)
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(body);
    }

    private JsonNode postRefresh(String refreshToken, org.springframework.test.web.servlet.ResultMatcher expected)
            throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
                .andExpect(expected)
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.isBlank() ? objectMapper.nullNode() : objectMapper.readTree(body);
    }

    private static GoogleIdToken googleToken(String email, boolean emailVerified) {
        GoogleIdToken.Payload payload = new GoogleIdToken.Payload();
        payload.setEmail(email);
        payload.setEmailVerified(emailVerified);
        return new GoogleIdToken(new JsonWebSignature.Header(), payload, new byte[0], new byte[0]);
    }
}
