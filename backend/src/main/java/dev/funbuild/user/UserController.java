package dev.funbuild.user;

import dev.funbuild.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

  private final UserService service;

  public UserController(UserService service) {
    this.service = service;
  }

  @GetMapping("/me")
  public UserSummary me(@AuthenticationPrincipal AuthenticatedUser principal) {
    return UserSummary.from(service.get(principal.id()));
  }

  @PutMapping("/me")
  public UserSummary updateMe(
      @AuthenticationPrincipal AuthenticatedUser principal,
      @Valid @RequestBody UpdateProfileRequest req) {
    return UserSummary.from(service.updateDisplayName(principal.id(), req.displayName()));
  }

  @GetMapping
  @PreAuthorize("hasRole('ADMIN')")
  public List<UserSummary> list() {
    return service.listAll().stream().map(UserSummary::from).toList();
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public UserSummary updateRole(@PathVariable UUID id, @Valid @RequestBody UserRoleUpdateRequest req) {
    return UserSummary.from(service.updateRole(id, req.role()));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
