package dev.funbuild.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

  Optional<User> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);

  Optional<User> findByAuthProviderAndProviderId(AuthProvider authProvider, String providerId);

  List<User> findAllByOrderByCreatedAtDesc();
}
