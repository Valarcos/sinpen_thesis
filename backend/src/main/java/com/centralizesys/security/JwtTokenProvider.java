package com.centralizesys.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;


@Component
public class JwtTokenProvider {

    private final String jwtSecret;

    private final int jwtExpirationInMs;

    public JwtTokenProvider(@Value("${app.jwtSecret}") String jwtSecret,
                            @Value("${app.jwtExpirationInMs}") int jwtExpirationInMs) {
        if (jwtSecret == null || jwtSecret.isBlank() || "UNSET".equals(jwtSecret)) {
            throw new IllegalArgumentException("JWT_SECRET must be configured securely in the environment!");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalArgumentException("JWT_SECRET must be at least 32 characters long for HS512!");
        }
        this.jwtSecret = jwtSecret;
        this.jwtExpirationInMs = jwtExpirationInMs;
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Instant nowInstant = Instant.now();
        return Jwts.builder()
                .setId(UUID.randomUUID().toString())      // jti claim (RFC 7519 §4.1.7)
                .setSubject(username)
                .setIssuedAt(java.util.Date.from(nowInstant))
                .setExpiration(java.util.Date.from(nowInstant.plusMillis(jwtExpirationInMs)))
                .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()), SignatureAlgorithm.HS512)
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Extracts the JWT ID (jti) claim from the token.
     * Used to identify a specific session in the active_tokens table and in-memory cache.
     *
     * @param token the raw JWT string.
     * @return the UUID string stored in the jti claim.
     */
    public String getJtiFromToken(String token) {
        return parseClaims(token).getId();
    }

    /**
     * Extracts the expiration timestamp from the token as a {@link LocalDateTime}.
     * Used when persisting the token to active_tokens so the cleanup task knows when to prune it.
     *
     * @param token the raw JWT string.
     * @return the token's expiration as a system-zone LocalDateTime.
     */
    public LocalDateTime getExpirationFromToken(String token) {
        return parseClaims(token).getExpiration()
                .toInstant()
                .atZone(ZoneId.of("America/Argentina/Buenos_Aires"))
                .toLocalDateTime();
    }

    public boolean validateToken(String authToken) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .build()
                    .parseClaimsJws(authToken);
            return true;
        } catch (SecurityException | MalformedJwtException | ExpiredJwtException | UnsupportedJwtException
                 | IllegalArgumentException e) {
            // Invalid JWT signature, Expired, Unsupported, or Empty
        }
        return false;
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
