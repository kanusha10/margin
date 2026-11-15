package com.margin;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {
    private final SecretKey key;
    private final long ttlMillis;

    public JwtService(@Value("${app.jwt-secret}") String secret, @Value("${app.jwt-hours}") long hours) {
        if (secret == null || secret.length() < 32)
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long.");
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.ttlMillis = hours * 3_600_000L;
    }

    public String issue(long userId, String email) {
        long now = System.currentTimeMillis();
        return Jwts.builder().subject(String.valueOf(userId)).claim("email", email)
                .issuedAt(new Date(now)).expiration(new Date(now + ttlMillis)).signWith(key).compact();
    }

    /** Returns the user id if the token is valid and unexpired. */
    public Optional<Long> parse(String token) {
        try {
            String sub = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload().getSubject();
            return Optional.of(Long.parseLong(sub));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
