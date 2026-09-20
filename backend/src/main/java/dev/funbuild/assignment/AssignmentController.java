package dev.funbuild.assignment;

import dev.funbuild.security.AuthenticatedUser;
import dev.funbuild.user.UserService;
import io.repsy.core.response.dtos.RestResponse;
import io.repsy.core.response.services.RestResponseFactory;
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
  private final RestResponseFactory responses;

  public AssignmentController(AssignmentService service, UserService userService, RestResponseFactory responses) {
    this.service = service;
    this.userService = userService;
    this.responses = responses;
  }

  @GetMapping
  public List<AssignmentResponse> list(@RequestParam(required = false) String status) {
    return "active".equalsIgnoreCase(status) ? service.listActive() : service.listAll();
  }

  @GetMapping("/{slug}")
  public AssignmentResponse get(@PathVariable String slug) {
    return service.get(slug);
  }

  @PostMapping
  @PreAuthorize("hasRole('ADMIN')")
  public AssignmentResponse create(
      @Valid @RequestBody AssignmentRequest req, @AuthenticationPrincipal AuthenticatedUser principal) {
    return service.create(req, userService.get(principal.id()));
  }

  @PutMapping("/{slug}")
  @PreAuthorize("hasRole('ADMIN')")
  public AssignmentResponse update(@PathVariable String slug, @Valid @RequestBody AssignmentRequest req) {
    return service.update(slug, req);
  }

  @DeleteMapping("/{slug}")
  @PreAuthorize("hasRole('ADMIN')")
  public RestResponse<Void> delete(@PathVariable String slug) {
    service.delete(slug);
    return responses.success("assignment.deleted");
  }
}
