package dev.funbuild.security;

import dev.funbuild.user.Role;
import dev.funbuild.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtService {

  private final SecretKey key;
  private final long expirationMinutes;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.expiration-minutes}") long expirationMinutes) {
    this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.expirationMinutes = expirationMinutes;
  }

  public String generateToken(User user) {
    return generateToken(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole());
  }

  public String generateToken(Long userId, String email, String displayName, Role role) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(String.valueOf(userId))
        .claim("email", email)
        .claim("name", displayName)
        .claim("role", role.name())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
        .signWith(key)
        .compact();
  }

  /** Returns empty for a missing, malformed or expired token rather than throwing. */
  public Optional<AuthenticatedUser> parse(String token) {
    try {
      Claims claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
      Long id = Long.valueOf(claims.getSubject());
      String email = claims.get("email", String.class);
      String name = claims.get("name", String.class);
      Role role = Role.valueOf(claims.get("role", String.class));
      return Optional.of(new AuthenticatedUser(id, email, name, role));
    } catch (JwtException | IllegalArgumentException ex) {
      return Optional.empty();
    }
  }
}
