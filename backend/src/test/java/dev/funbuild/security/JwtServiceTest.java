package dev.funbuild.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.funbuild.user.Role;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private final JwtService jwtService = new JwtService(SecurityTestConfig.TEST_JWT_SECRET, 60);

  @Test
  void roundTripsAToken() {
    UUID id = UUID.randomUUID();
    String token = jwtService.generateToken(id, "admin@funbuild.dev", "Admin", Role.ADMIN);

    var parsed = jwtService.parse(token);

    assertTrue(parsed.isPresent());
    assertEquals(id, parsed.get().id());
    assertEquals(Role.ADMIN, parsed.get().role());
    assertEquals("admin@funbuild.dev", parsed.get().email());
  }

  @Test
  void rejectsAMalformedToken() {
    assertFalse(jwtService.parse("not-a-jwt").isPresent());
  }

  @Test
  void rejectsATokenSignedWithADifferentSecret() {
    JwtService other = new JwtService("a-completely-different-test-secret-0123456789", 60);
    String token = other.generateToken(UUID.randomUUID(), "admin@funbuild.dev", "Admin", Role.ADMIN);

    assertFalse(jwtService.parse(token).isPresent());
  }
}
