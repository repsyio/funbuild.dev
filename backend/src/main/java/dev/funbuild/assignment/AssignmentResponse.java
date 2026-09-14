package dev.funbuild.assignment;

import dev.funbuild.user.UserSummary;
import java.time.Instant;

public record AssignmentResponse(
    Long id,
    String title,
    String description,
    Instant startAt,
    Instant endAt,
    AssignmentStatus status,
    UserSummary createdBy,
    Instant createdAt) {

  public static AssignmentResponse from(Assignment assignment) {
    return new AssignmentResponse(
        assignment.getId(),
        assignment.getTitle(),
        assignment.getDescription(),
        assignment.getStartAt(),
        assignment.getEndAt(),
        assignment.status(),
        UserSummary.from(assignment.getCreatedBy()),
        assignment.getCreatedAt());
  }
}
