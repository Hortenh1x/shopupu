package com.example.shopupu.security;

import com.example.shopupu.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.function.Function;
import javax.crypto.SecretKey;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long jwtExpiration;
    private final String issuer;
    private final String audience;

    public JwtTokenProvider(JwtProperties properties) {
        byte[] secretBytes = properties.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalArgumentException("JWT secret must be at least 32 bytes for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
        this.jwtExpiration = properties.getAccessTokenTtlMin() * 60 * 1000;
        this.issuer = properties.getIssuer();
        this.audience = properties.getAudience();
    }

    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        try {
            Claims claims = extractAllClaims(token);
            return claimsResolver.apply(claims);
        } catch (ExpiredJwtException e) {
            return claimsResolver.apply(e.getClaims());
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .requireAudience(audience)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String generateToken(UserDetails userDetails) {
        return generateTokenWithExpiration(userDetails, jwtExpiration);
    }

    private String generateTokenWithExpiration(UserDetails userDetails, long expirationMs) {
        Date now = new Date(System.currentTimeMillis());

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            String username = extractUsername(token);
            return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        Date expiration = extractClaim(token, Claims::getExpiration);
        return expiration.before(new Date());
    }
}
