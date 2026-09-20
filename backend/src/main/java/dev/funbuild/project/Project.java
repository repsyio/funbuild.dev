package dev.funbuild.project;

import dev.funbuild.assignment.Assignment;
import dev.funbuild.techlabel.TechLabel;
import dev.funbuild.user.User;
import io.repsy.core.uuidv7.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import org.hibernate.annotations.Formula;

@Entity
@Table(name = "projects")
public class Project {

  @Id
  @UuidV7
  private UUID id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "assignment_id", nullable = false)
  private Assignment assignment;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "submitter_id", nullable = false)
  private User submitter;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(name = "showcase_url", nullable = false, length = 500)
  private String showcaseUrl;

  @Column(name = "git_repo_url", length = 500)
  private String gitRepoUrl;

  @ManyToMany
  @JoinTable(
      name = "project_tech_labels",
      joinColumns = @JoinColumn(name = "project_id"),
      inverseJoinColumns = @JoinColumn(name = "tech_label_id"))
  private Set<TechLabel> techLabels = new LinkedHashSet<>();

  /** Kept in sync with the votes table by Hibernate on every read; never written directly. */
  @Formula("(SELECT COUNT(*) FROM votes v WHERE v.project_id = id)")
  private long voteCount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  protected Project() {}

  public Project(
      Assignment assignment, User submitter, String title, String description, String showcaseUrl, String gitRepoUrl) {
    this.assignment = assignment;
    this.submitter = submitter;
    this.title = title;
    this.description = description;
    this.showcaseUrl = showcaseUrl;
    this.gitRepoUrl = gitRepoUrl;
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public void update(String title, String description, String showcaseUrl, String gitRepoUrl) {
    this.title = title;
    this.description = description;
    this.showcaseUrl = showcaseUrl;
    this.gitRepoUrl = gitRepoUrl;
  }

  public void setTechLabels(Set<TechLabel> techLabels) {
    this.techLabels = techLabels;
  }

  @PreUpdate
  void onUpdate() {
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public Assignment getAssignment() {
    return assignment;
  }

  public User getSubmitter() {
    return submitter;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public String getShowcaseUrl() {
    return showcaseUrl;
  }

  public String getGitRepoUrl() {
    return gitRepoUrl;
  }

  public Set<TechLabel> getTechLabels() {
    return techLabels;
  }

  public long getVoteCount() {
    return voteCount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
