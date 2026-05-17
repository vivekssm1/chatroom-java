package com.chatroom.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Equivalent to these helpers in server.js:
 *
 *   function signToken(userId, role)  { return jwt.sign({id, role}, SECRET, {expiresIn:"30d"}) }
 *   function signAdminToken()         { return jwt.sign({role:"admin"}, SECRET, {expiresIn:"8h"}) }
 *   function verifyToken(token)       { try { return jwt.verify(token, SECRET) } catch { return null } }
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiry.user:2592000}")
    private long userExpirySeconds;

    @Value("${jwt.expiry.admin:28800}")
    private long adminExpirySeconds;

    // ── signToken(userId, "user") ─────────────────────────────────────────────
    public String generateUserToken(String userId) {
        return buildToken(userId, "user", userExpirySeconds * 1000);
    }

    // ── signAdminToken() ──────────────────────────────────────────────────────
    public String generateAdminToken() {
        return buildToken(null, "admin", adminExpirySeconds * 1000);
    }

    private String buildToken(String userId, String role, long expiryMs) {
        JwtBuilder builder = Jwts.builder()
                .claim("role", role)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiryMs))
                .signWith(getKey());

        if (userId != null) builder.claim("id", userId);
        return builder.compact();
    }

    // ── verifyToken(token) — returns null on failure (same as Node) ───────────
    public Claims verifyToken(String token) {
        if (token == null || token.isBlank()) return null;
        try {
            return Jwts.parser()
                    .verifyWith(getKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (JwtException e) {
            return null;  // expired, invalid signature, malformed — all return null
        }
    }

    public String extractUserId(String token) {
        Claims claims = verifyToken(token);
        return claims != null ? claims.get("id", String.class) : null;
    }

    public String extractRole(String token) {
        Claims claims = verifyToken(token);
        return claims != null ? claims.get("role", String.class) : null;
    }

    public boolean isAdmin(String token) {
        return "admin".equals(extractRole(token));
    }

    private SecretKey getKey() {
        // Pad secret to at least 32 bytes for HMAC-SHA256
        String padded = secret.length() >= 32 ? secret : secret + "0".repeat(32 - secret.length());
        return Keys.hmacShaKeyFor(padded.getBytes(StandardCharsets.UTF_8));
    }
}
