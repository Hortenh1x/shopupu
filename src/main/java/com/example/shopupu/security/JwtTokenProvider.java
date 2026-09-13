package com.example.shopupu.security;

import com.example.shopupu.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
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
        return generateToken(userDetails, null);
    }

    public String generateToken(UserDetails userDetails, Instant mfaVerifiedAt) {
        long expirationMs = Math.min(jwtExpiration, Duration.ofMinutes(15).toMillis());
        Date now = new Date(System.currentTimeMillis());

        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("authVersion", userDetails instanceof ShopUserDetails principal ? principal.authVersion() : 0L)
                .claim("mfaVerifiedAt", mfaVerifiedAt == null ? null : mfaVerifiedAt.getEpochSecond())
                .issuer(issuer)
                .audience().add(audience).and()
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expirationMs))
                .signWith(key)
                .compact();
    }

    public boolean isTokenValid(String token, UserDetails userDetails) {
        try {
            Claims claims = extractAllClaims(token);
            if (!userDetails.isEnabled() || !claims.getSubject().equals(userDetails.getUsername())) return false;
            Number version = claims.get("authVersion", Number.class);
            long currentVersion = userDetails instanceof ShopUserDetails principal ? principal.authVersion() : 0;
            if (version == null || version.longValue() != currentVersion) return false;
            boolean privileged = userDetails.getAuthorities().stream().anyMatch(a ->
                    a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equals("ROLE_MANAGER"));
            if (privileged) {
                Number assurance = claims.get("mfaVerifiedAt", Number.class);
                if (!(userDetails instanceof ShopUserDetails principal) || !principal.mfaEnrolled() || assurance == null) return false;
                Instant verified = Instant.ofEpochSecond(assurance.longValue());
                if (verified.isAfter(Instant.now()) || !verified.plus(Duration.ofHours(12)).isAfter(Instant.now())) return false;
            }
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }

}
