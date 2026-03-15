package com.aether.app.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expirationMs;

    public JwtService(@Value("${aether.jwt.secret}") String secret,
                      @Value("${aether.jwt.expiration-ms}") long expirationMs) {
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
    }

    public String generateToken(UserProfile profile) {
        return Jwts.builder()
                .subject(profile.getId())
                .claim("tenantId", profile.getTenantId())
                .claim("username", profile.getUsername())
                .claim("role", profile.getRole().name())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and validates a JWT, returning its claims.
     * Throws {@link JwtException} if the token is invalid or expired.
     */
    public Claims validateAndExtractClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractUserId(String token) {
        return validateAndExtractClaims(token).getSubject();
    }

    public String extractTenantId(String token) {
        return validateAndExtractClaims(token).get("tenantId", String.class);
    }
}
