package dev.funbuild.user;

public record UserSummary(
    Long id, String email, String displayName, String avatarUrl, Role role, AuthProvider authProvider) {

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
