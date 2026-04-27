package com.trustplatform.auth.auth.security;

import com.trustplatform.auth.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService {
    static final String USER_ID_CLAIM = "userId";
    static final String EMAIL_CLAIM = "email";
    static final String ROLE_CLAIM = "role";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey signingKey;

    @PostConstruct
    void initSigningKey() {
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(User user) {
        return Jwts.builder()
                .subject(user.getEmail())
                .claim(USER_ID_CLAIM, user.getId().toString())
                .claim(EMAIL_CLAIM, user.getEmail())
                .claim(ROLE_CLAIM, user.getRole())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractEmailClaim(String token) {
        return extractAllClaims(token).get(EMAIL_CLAIM, String.class);
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).get(USER_ID_CLAIM, String.class);
    }

    public String extractRole(String token) {
        return extractAllClaims(token).get(ROLE_CLAIM, String.class);
    }

    public boolean isTokenValid(String token, User user) {
        String subjectEmail = extractEmail(token);
        String emailClaim = extractEmailClaim(token);
        String userIdClaim = extractUserId(token);
        String roleClaim = extractRole(token);

        return subjectEmail.equals(user.getEmail())
                && emailClaim != null
                && emailClaim.equals(user.getEmail())
                && userIdClaim != null
                && userIdClaim.equals(user.getId().toString())
                && roleClaim != null
                && roleClaim.equals(user.getRole())
                && !isTokenExpired(token);
    }

    private boolean isTokenExpired(String token) {
        return extractAllClaims(token).getExpiration().before(new Date());
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }
}
