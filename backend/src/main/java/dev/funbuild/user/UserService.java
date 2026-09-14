package dev.funbuild.user;

import dev.funbuild.error.ConflictException;
import dev.funbuild.error.NotFoundException;
import dev.funbuild.error.UnauthorizedException;
import java.util.List;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository repository;
  private final PasswordEncoder passwordEncoder;

  public UserService(UserRepository repository, PasswordEncoder passwordEncoder) {
    this.repository = repository;
    this.passwordEncoder = passwordEncoder;
  }

  public List<User> listAll() {
    return repository.findAllByOrderByCreatedAtDesc();
  }

  public User get(Long id) {
    return repository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
  }

  @Transactional
  public User register(String email, String rawPassword) {
    if (repository.existsByEmailIgnoreCase(email)) {
      throw new ConflictException("Email is already registered");
    }
    User user =
        new User(
            email,
            passwordEncoder.encode(rawPassword),
            defaultDisplayName(email),
            null,
            Role.MEMBER,
            AuthProvider.LOCAL,
            null);
    return repository.save(user);
  }

  /** New accounts start with the email's local part as a placeholder name, editable on the profile page. */
  private static String defaultDisplayName(String email) {
    String localPart = email.split("@", 2)[0];
    return localPart.isBlank() ? "Member" : localPart;
  }

  public User authenticate(String email, String rawPassword) {
    User user =
        repository
            .findByEmailIgnoreCase(email)
            .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));
    if (user.getPasswordHash() == null
        || !passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
      throw new UnauthorizedException("Invalid email or password");
    }
    return user;
  }

  /** Finds the user for an OAuth2 login, links the provider to a matching email, or creates one. */
  @Transactional
  public User findOrCreateOAuthUser(
      AuthProvider provider, String providerId, String email, String name, String avatarUrl) {
    Optional<User> byProvider = repository.findByAuthProviderAndProviderId(provider, providerId);
    if (byProvider.isPresent()) {
      return byProvider.get();
    }
    if (email != null) {
      Optional<User> byEmail = repository.findByEmailIgnoreCase(email);
      if (byEmail.isPresent()) {
        User existing = byEmail.get();
        existing.linkProvider(provider, providerId);
        return existing;
      }
    }
    String resolvedEmail =
        email != null
            ? email
            : "%s-%s@users.noreply.funbuild.dev".formatted(provider.name().toLowerCase(), providerId);
    User user =
        new User(
            resolvedEmail,
            null,
            name != null && !name.isBlank() ? name : "Member",
            avatarUrl,
            Role.MEMBER,
            provider,
            providerId);
    return repository.save(user);
  }

  @Transactional
  public User updateRole(Long id, Role role) {
    User user = get(id);
    user.setRole(role);
    return user;
  }

  @Transactional
  public User updateDisplayName(Long id, String displayName) {
    User user = get(id);
    user.setDisplayName(displayName);
    return user;
  }

  @Transactional
  public void delete(Long id) {
    if (!repository.existsById(id)) {
      throw new NotFoundException("User not found");
    }
    repository.deleteById(id);
  }
}
