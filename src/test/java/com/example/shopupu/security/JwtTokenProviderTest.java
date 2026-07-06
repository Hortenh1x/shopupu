package com.example.shopupu.security;

import static org.junit.jupiter.api.Assertions.*;

import com.example.shopupu.config.JwtProperties;
import io.jsonwebtoken.Claims;
import java.util.Collections;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static JwtProperties props(String secret, long accessMin) {
        JwtProperties p = new JwtProperties();
        p.setSecret(secret);
        p.setAccessTokenTtlMin(accessMin);
        p.setRefreshTokenTtlDays(1);
        p.setIssuer("shopupu");
        p.setAudience("shopupu-api");
        return p;
    }

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                props("MySuperLongSecretKeyThatIsDefinitelySecure1234567890", 10));
    }

    @Test
    void testGenerateAndValidateToken() {
        UserDetails user = new User("testUser", "password", Collections.emptyList());
        String token = jwtTokenProvider.generateToken(user);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.isTokenValid(token, user));
        assertEquals("testUser", jwtTokenProvider.extractUsername(token));
    }

    @Test
    void testExpiredToken() throws InterruptedException {
        JwtTokenProvider shortLived = new JwtTokenProvider(
                props("AnotherVeryStrongKeyThatIsLongEnough1234567890", 0));

        UserDetails user = new User("user", "pass", Collections.emptyList());
        String token = shortLived.generateToken(user);

        Thread.sleep(1000);
        assertFalse(shortLived.isTokenValid(token, user));
    }

    @Test
    void testInvalidSignatureToken() {
        UserDetails user = new User("bob", "pass", Collections.emptyList());
        String validToken = jwtTokenProvider.generateToken(user);

        JwtTokenProvider other = new JwtTokenProvider(
                props("CompletelyDifferentSecretKey987654321xxxx", 10));

        assertFalse(other.isTokenValid(validToken, user));
    }

    @Test
    void testMalformedToken() {
        UserDetails user = new User("alice", "pass", Collections.emptyList());
        String token = jwtTokenProvider.generateToken(user);

        String brokenToken = token.substring(0, token.length() / 2);

        assertFalse(jwtTokenProvider.isTokenValid(brokenToken, user));
    }

    @Test
    void testExtractClaims() {
        UserDetails user = new User("charlie", "pass", Collections.emptyList());
        String token = jwtTokenProvider.generateToken(user);

        String username = jwtTokenProvider.extractUsername(token);
        assertEquals("charlie", username);

        Date expiration = jwtTokenProvider.extractClaim(token, Claims::getExpiration);
        assertNotNull(expiration);
        assertTrue(expiration.after(new Date()));
    }

    @Test
    void testTokenWithDifferentIssuerRejected() {
        UserDetails user = new User("issuerUser", "pass", Collections.emptyList());

        JwtProperties otherIssuer = props("MySuperLongSecretKeyThatIsDefinitelySecure1234567890", 10);
        otherIssuer.setIssuer("evil-issuer");
        String foreignToken = new JwtTokenProvider(otherIssuer).generateToken(user);

        assertFalse(jwtTokenProvider.isTokenValid(foreignToken, user));
    }

    @Test
    void testTokenWithDifferentAudienceRejected() {
        UserDetails user = new User("audUser", "pass", Collections.emptyList());

        JwtProperties otherAud = props("MySuperLongSecretKeyThatIsDefinitelySecure1234567890", 10);
        otherAud.setAudience("other-api");
        String foreignToken = new JwtTokenProvider(otherAud).generateToken(user);

        assertFalse(jwtTokenProvider.isTokenValid(foreignToken, user));
    }

    @Test
    void testTokenWithDifferentUser() {
        UserDetails user = new User("john", "pass", Collections.emptyList());
        String token = jwtTokenProvider.generateToken(user);

        UserDetails hacker = new User("evil", "pass", Collections.emptyList());
        assertFalse(jwtTokenProvider.isTokenValid(token, hacker));
    }
}
