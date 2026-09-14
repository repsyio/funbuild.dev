package dev.funbuild.assignment;

import dev.funbuild.security.AuthenticatedUser;
import dev.funbuild.user.UserService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
public class AssignmentController {

  private final AssignmentService service;
  private final UserService userService;

  public AssignmentController(AssignmentService service, UserService userService) {
    this.service = service;
    this.userService = userService;
  }

  @GetMapping
  public List<AssignmentResponse> list(@RequestParam(required = false) String status) {
    return "active".equalsIgnoreCase(status) ? service.listActive() : service.listAll();
  }

  @GetMapping("/{id}")
  public AssignmentResponse get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public AssignmentResponse create(
      @Valid @RequestBody AssignmentRequest req, @AuthenticationPrincipal AuthenticatedUser principal) {
    return service.create(req, userService.get(principal.id()));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public AssignmentResponse update(@PathVariable Long id, @Valid @RequestBody AssignmentRequest req) {
    return service.update(id, req);
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasRole('ADMIN')")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    service.delete(id);
    return ResponseEntity.noContent().build();
  }
}
