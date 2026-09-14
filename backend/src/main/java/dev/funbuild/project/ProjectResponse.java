package dev.funbuild.project;

import dev.funbuild.techlabel.TechLabelResponse;
import dev.funbuild.user.UserSummary;
import java.time.Instant;
import java.util.List;

public record ProjectResponse(
    Long id,
    Long assignmentId,
    String assignmentTitle,
    UserSummary submitter,
    String title,
    String description,
    String showcaseUrl,
    String gitRepoUrl,
    List<TechLabelResponse> techLabels,
    long voteCount,
    boolean votedByMe,
    Instant createdAt,
    Instant updatedAt) {

  public static ProjectResponse from(Project project, boolean votedByMe) {
    return new ProjectResponse(
        project.getId(),
        project.getAssignment().getId(),
        project.getAssignment().getTitle(),
        UserSummary.from(project.getSubmitter()),
        project.getTitle(),
        project.getDescription(),
        project.getShowcaseUrl(),
        project.getGitRepoUrl(),
        project.getTechLabels().stream().map(TechLabelResponse::from).toList(),
        project.getVoteCount(),
        votedByMe,
        project.getCreatedAt(),
        project.getUpdatedAt());
  }
}
