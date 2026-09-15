package dev.funbuild.vote;

import dev.funbuild.security.AuthenticatedUser;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects/{id}/vote")
public class VoteController {

  private final VoteService service;

  public VoteController(VoteService service) {
    this.service = service;
  }

  /** Toggles the current member's vote on the project. */
  @PostMapping
  public VoteResponse toggle(@PathVariable UUID id, @AuthenticationPrincipal AuthenticatedUser voter) {
    return service.toggle(id, voter);
  }
}
