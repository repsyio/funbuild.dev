package dev.funbuild.vote;

import dev.funbuild.error.NotFoundException;
import dev.funbuild.project.Project;
import dev.funbuild.project.ProjectRepository;
import dev.funbuild.security.AuthenticatedUser;
import dev.funbuild.user.UserService;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoteService {

  private final VoteRepository voteRepository;
  private final ProjectRepository projectRepository;
  private final UserService userService;

  public VoteService(VoteRepository voteRepository, ProjectRepository projectRepository, UserService userService) {
    this.voteRepository = voteRepository;
    this.projectRepository = projectRepository;
    this.userService = userService;
  }

  /** Toggles the current user's vote on a project and returns the resulting state. */
  @Transactional
  public VoteResponse toggle(Long projectId, AuthenticatedUser voter) {
    Project project =
        projectRepository.findById(projectId).orElseThrow(() -> new NotFoundException("Project not found"));
    Optional<Vote> existing = voteRepository.findByProjectIdAndVoterId(projectId, voter.id());
    boolean votedByMe;
    if (existing.isPresent()) {
      voteRepository.delete(existing.get());
      votedByMe = false;
    } else {
      voteRepository.save(new Vote(project, userService.get(voter.id())));
      votedByMe = true;
    }
    return new VoteResponse(voteRepository.countByProjectId(projectId), votedByMe);
  }
}
