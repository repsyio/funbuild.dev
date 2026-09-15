package dev.funbuild.security;

import dev.funbuild.user.Role;
import java.util.UUID;

/** The JWT-derived principal set on the security context for every authenticated request. */
public record AuthenticatedUser(UUID id, String email, String displayName, Role role) {

  public boolean isAdmin() {
    return role == Role.ADMIN;
  }
}
