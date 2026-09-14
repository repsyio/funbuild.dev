package dev.funbuild.user;

import java.util.UUID;

public record UserSummary(
    UUID id, String email, String displayName, String avatarUrl, Role role, AuthProvider authProvider) {

  public static UserSummary from(User user) {
    return new UserSummary(
        user.getId(),
        user.getEmail(),
        user.getDisplayName(),
        user.getAvatarUrl(),
        user.getRole(),
        user.getAuthProvider());
  }
}
