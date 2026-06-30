package com.networkscanner.backend.users.impl;

import com.networkscanner.backend.users.api.JwtService;
import com.networkscanner.backend.users.model.AppUser;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtServiceImpl implements JwtService {

  private final Key signingKey;
  private final long expirationMs;

  public JwtServiceImpl(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.expiration-ms}") long expirationMs
  ) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMs = expirationMs;
  }

  @Override
  public String generateToken(AppUser user) {
    Date issuedAt = new Date();
    Date expiration = new Date(issuedAt.getTime() + expirationMs);
    List<String> roles = user.getRoles().stream()
        .map(Enum::name)
        .toList();

    return Jwts.builder()
        .subject(user.getEmail())
        .claim("displayName", user.getDisplayName())
        .claim("roles", roles)
        .issuedAt(issuedAt)
        .expiration(expiration)
        .signWith(signingKey)
        .compact();
  }

  @Override
  public String extractEmail(String token) {
    return parseClaims(token).getSubject();
  }

  @Override
  public boolean isTokenValid(String token) {
    try {
      Claims claims = parseClaims(token);
      return claims.getExpiration() != null && claims.getExpiration().after(new Date());
    } catch (RuntimeException exception) {
      return false;
    }
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith((javax.crypto.SecretKey) signingKey)
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }
}
