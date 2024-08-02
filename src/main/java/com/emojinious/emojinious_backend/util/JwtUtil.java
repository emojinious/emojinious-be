package com.emojinious.emojinious_backend.util;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.Key;
import java.util.Date;
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes());
    }

    public String generateToken(String playerId, String sessionId, boolean isHost) {
        return Jwts.builder()
                .setSubject(playerId)
                .claim("sessionId", sessionId)
                .claim("isHost", isHost)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String refreshToken(String token) {
        Claims claims = validateToken(token);
        return generateToken(claims.getSubject(), claims.get("sessionId", String.class), claims.get("isHost", Boolean.class));
    }


    public Claims validateToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}