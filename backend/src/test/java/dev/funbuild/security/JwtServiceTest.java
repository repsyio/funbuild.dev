package dev.funbuild.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.funbuild.user.Role;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private final JwtService jwtService = new JwtService(SecurityTestConfig.TEST_JWT_SECRET, 60);

  @Test
  void roundTripsAToken() {
    String token = jwtService.generateToken(1L, "admin@funbuild.dev", "Admin", Role.ADMIN);

    var parsed = jwtService.parse(token);

    assertTrue(parsed.isPresent());
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
    String token = other.generateToken(1L, "admin@funbuild.dev", "Admin", Role.ADMIN);

    assertFalse(jwtService.parse(token).isPresent());
  }
}
