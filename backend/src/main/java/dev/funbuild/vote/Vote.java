package dev.funbuild.vote;

import dev.funbuild.project.Project;
import dev.funbuild.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import io.repsy.core.uuidv7.UuidV7;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "votes")
public class Vote {

  @Id
  @UuidV7
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "project_id", nullable = false)
  private Project project;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "voter_id", nullable = false)
  private User voter;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Vote() {}

  public Vote(Project project, User voter) {
    this.project = project;
    this.voter = voter;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Project getProject() {
    return project;
  }

  public User getVoter() {
    return voter;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
