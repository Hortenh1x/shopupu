package com.example.shopupu.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

/**
 * describes the JwtTokenProviderTest test class.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    @BeforeEach
    // handles setUp.
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(
                "MySuperLongSecretKeyThatIsDefinitelySecure1234567890",
                10,
                1
        );
    }

    @Test
    // handles testGenerateAndValidateToken.
    void testGenerateAndValidateToken() {
        UserDetails user = new User("testUser", "password", Collections.emptyList());
        String token = jwtTokenProvider.generateToken(user);

        assertNotNull(token);
        assertTrue(jwtTokenProvider.isTokenValid(token, user));
        assertEquals("testUser", jwtTokenProvider.extractUsername(token));
    }

    @Test
    // handles testExpiredToken.
    void testExpiredToken() throws InterruptedException {
        JwtTokenProvider shortLived = new JwtTokenProvider(
                "AnotherVeryStrongKeyThatIsLongEnough1234567890",
                0,
                1
        );

        UserDetails user = new User("user", "pass", Collections.emptyList());
        String token = shortLived.generateToken(user);

        Thread.sleep(1000);
        assertFalse(shortLived.isTokenValid(token, user));
    }

    @Test
    // handles testInvalidSignatureToken.
    void testInvalidSignatureToken() {
        UserDetails user = new User("bob", "pass", Collections.emptyList());
        String validToken = jwtTokenProvider.generateToken(user);


        JwtTokenProvider other = new JwtTokenProvider(
                "CompletelyDifferentSecretKey987654321",
                10,
                1
        );

        assertFalse(other.isTokenValid(validToken, user));
    }

    @Test
    // handles testMalformedToken.
    void testMalformedToken() {
        UserDetails user = new User("alice", "pass", Collections.emptyList());
        String token = jwtTokenProvider.generateToken(user);


        String brokenToken = token.substring(0, token.length() / 2);

        assertFalse(jwtTokenProvider.isTokenValid(brokenToken, user));
    }

    @Test
    // handles testExtractClaims.
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
    // handles testRefreshTokenValidity.
    void testRefreshTokenValidity() {
        UserDetails user = new User("refreshUser", "pass", Collections.emptyList());
        String refresh = jwtTokenProvider.generateRefreshToken(user);

        assertNotNull(refresh);
        assertTrue(jwtTokenProvider.isTokenValid(refresh, user));
    }

    @Test
    // handles testTokenWithDifferentUser.
    void testTokenWithDifferentUser() {
        UserDetails user = new User("john", "pass", Collections.emptyList());
        String token = jwtTokenProvider.generateToken(user);

        UserDetails hacker = new User("evil", "pass", Collections.emptyList());
        assertFalse(jwtTokenProvider.isTokenValid(token, hacker));
    }




}
