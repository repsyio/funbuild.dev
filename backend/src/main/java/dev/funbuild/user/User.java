package dev.funbuild.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "users")
public class User {

  @Id
  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  private UUID id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "display_name", nullable = false, length = 120)
  private String displayName;

  @Column(name = "avatar_url", length = 500)
  private String avatarUrl;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private Role role;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_provider", nullable = false, length = 20)
  private AuthProvider authProvider;

  @Column(name = "provider_id")
  private String providerId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected User() {}

  public User(
      String email,
      String passwordHash,
      String displayName,
      String avatarUrl,
      Role role,
      AuthProvider authProvider,
      String providerId) {
    this.email = email;
    this.passwordHash = passwordHash;
    this.displayName = displayName;
    this.avatarUrl = avatarUrl;
    this.role = role;
    this.authProvider = authProvider;
    this.providerId = providerId;
    this.createdAt = Instant.now();
  }

  public void linkProvider(AuthProvider provider, String providerId) {
    this.authProvider = provider;
    this.providerId = providerId;
  }

  public void setRole(Role role) {
    this.role = role;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public void setAvatarUrl(String avatarUrl) {
    this.avatarUrl = avatarUrl;
  }

  public UUID getId() {
    return id;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public String getDisplayName() {
    return displayName;
  }

  public String getAvatarUrl() {
    return avatarUrl;
  }

  public Role getRole() {
    return role;
  }

  public AuthProvider getAuthProvider() {
    return authProvider;
  }

  public String getProviderId() {
    return providerId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
