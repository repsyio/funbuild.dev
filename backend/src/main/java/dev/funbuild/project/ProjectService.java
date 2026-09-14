package dev.funbuild.project;

import dev.funbuild.assignment.Assignment;
import dev.funbuild.assignment.AssignmentRepository;
import dev.funbuild.assignment.AssignmentStatus;
import dev.funbuild.error.ConflictException;
import dev.funbuild.error.ForbiddenException;
import dev.funbuild.error.NotFoundException;
import dev.funbuild.security.AuthenticatedUser;
import dev.funbuild.techlabel.TechLabelService;
import dev.funbuild.user.Role;
import dev.funbuild.user.UserService;
import dev.funbuild.vote.VoteRepository;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Maps entities to response DTOs inside each {@code @Transactional} method: several fields
 * ({@code assignment}, {@code submitter}, {@code techLabels}) are lazy associations, and with
 * {@code spring.jpa.open-in-view: false} the Hibernate session is gone by the time a controller
 * would otherwise touch them.
 */
@Service
public class ProjectService {

  private final ProjectRepository repository;
  private final AssignmentRepository assignmentRepository;
  private final TechLabelService techLabelService;
  private final UserService userService;
  private final VoteRepository voteRepository;

  public ProjectService(
      ProjectRepository repository,
      AssignmentRepository assignmentRepository,
      TechLabelService techLabelService,
      UserService userService,
      VoteRepository voteRepository) {
    this.repository = repository;
    this.assignmentRepository = assignmentRepository;
    this.techLabelService = techLabelService;
    this.userService = userService;
    this.voteRepository = voteRepository;
  }

  @Transactional(readOnly = true)
  public List<ProjectResponse> list(Long assignmentId, AuthenticatedUser viewer) {
    List<Project> projects =
        assignmentId != null
            ? repository.findAllByAssignmentIdOrderByVoteCountDescCreatedAtDesc(assignmentId)
            : repository.findAllByOrderByVoteCountDescCreatedAtDesc();
    return toResponses(projects, viewer);
  }

  @Transactional(readOnly = true)
  public List<ProjectResponse> topVoted(int limit, AuthenticatedUser viewer) {
    List<Project> projects = repository.findAllByOrderByVoteCountDescCreatedAtDesc(PageRequest.of(0, limit));
    return toResponses(projects, viewer);
  }

  @Transactional(readOnly = true)
  public ProjectResponse get(Long id, AuthenticatedUser viewer) {
    Project project = find(id);
    return ProjectResponse.from(project, votedByMe(project, viewer));
  }

  @Transactional
  public ProjectResponse submit(ProjectRequest req, AuthenticatedUser submitter) {
    Assignment assignment =
        assignmentRepository
            .findById(req.assignmentId())
            .orElseThrow(() -> new NotFoundException("Assignment not found"));
    if (assignment.status() != AssignmentStatus.ACTIVE) {
      throw new ConflictException("This assignment is no longer accepting submissions");
    }
    var user = userService.get(submitter.id());
    Project project =
        new Project(assignment, user, req.title(), req.description(), req.showcaseUrl(), req.gitRepoUrl());
    project.setTechLabels(techLabelService.findOrCreateAll(req.techLabels(), user));
    Project saved = repository.save(project);
    return ProjectResponse.from(saved, false);
  }

  @Transactional
  public ProjectResponse update(Long id, ProjectRequest req, AuthenticatedUser currentUser) {
    Project project = find(id);
    requireOwnerOrAdmin(project, currentUser);
    if (project.getAssignment().status() != AssignmentStatus.ACTIVE) {
      throw new ConflictException("This assignment is no longer accepting changes");
    }
    project.update(req.title(), req.description(), req.showcaseUrl(), req.gitRepoUrl());
    project.setTechLabels(
        techLabelService.findOrCreateAll(req.techLabels(), userService.get(currentUser.id())));
    return ProjectResponse.from(project, votedByMe(project, currentUser));
  }

  @Transactional
  public void delete(Long id, AuthenticatedUser currentUser) {
    Project project = find(id);
    requireOwnerOrAdmin(project, currentUser);
    repository.delete(project);
  }

  private Project find(Long id) {
    return repository.findById(id).orElseThrow(() -> new NotFoundException("Project not found"));
  }

  private void requireOwnerOrAdmin(Project project, AuthenticatedUser currentUser) {
    boolean isOwner = project.getSubmitter().getId().equals(currentUser.id());
    if (!isOwner && currentUser.role() != Role.ADMIN) {
      throw new ForbiddenException("Not allowed to modify this project");
    }
  }

  private boolean votedByMe(Project project, AuthenticatedUser viewer) {
    if (viewer == null) {
      return false;
    }
    return voteRepository.existsByProjectIdAndVoterId(project.getId(), viewer.id());
  }

  private List<ProjectResponse> toResponses(List<Project> projects, AuthenticatedUser viewer) {
    if (projects.isEmpty()) {
      return List.of();
    }
    Set<Long> votedIds =
        viewer == null
            ? Collections.emptySet()
            : voteRepository.findVotedProjectIds(viewer.id(), projects.stream().map(Project::getId).toList());
    return projects.stream().map(p -> ProjectResponse.from(p, votedIds.contains(p.getId()))).toList();
  }
}
