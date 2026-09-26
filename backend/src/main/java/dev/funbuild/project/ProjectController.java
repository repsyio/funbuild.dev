package dev.funbuild.project;

import dev.funbuild.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
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
@RequestMapping("/api/projects")
public class ProjectController {

  private final ProjectService service;

  public ProjectController(ProjectService service) {
    this.service = service;
  }

  @GetMapping
  public List<ProjectResponse> list(
      @RequestParam(required = false) UUID assignmentId, @AuthenticationPrincipal AuthenticatedUser viewer) {
    return service.list(assignmentId, viewer);
  }

  /** Homepage leaderboard: all-time top-voted projects. */
  @GetMapping("/top")
  public List<ProjectResponse> top(
      @RequestParam(defaultValue = "10") int limit, @AuthenticationPrincipal AuthenticatedUser viewer) {
    return service.topVoted(Math.min(limit, 50), viewer);
  }

  @GetMapping("/{id}")
  public ProjectResponse get(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser viewer) {
    return service.get(id, viewer);
  }

  @PostMapping
  public ProjectResponse submit(
      @Valid @RequestBody ProjectRequest req, @AuthenticationPrincipal AuthenticatedUser submitter) {
    return service.submit(req, submitter);
  }

  @PutMapping("/{id}")
  public ProjectResponse update(
      @PathVariable UUID id,
      @Valid @RequestBody ProjectRequest req,
      @AuthenticationPrincipal AuthenticatedUser currentUser) {
    return service.update(id, req, currentUser);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser currentUser) {
    service.delete(id, currentUser);
    return ResponseEntity.noContent().build();
  }
}
