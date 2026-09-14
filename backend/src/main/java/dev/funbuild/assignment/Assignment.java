package dev.funbuild.assignment;

import dev.funbuild.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "assignments")
public class Assignment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String title;

  @Column(nullable = false, columnDefinition = "text")
  private String description;

  @Column(name = "start_at", nullable = false)
  private Instant startAt;

  @Column(name = "end_at", nullable = false)
  private Instant endAt;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by", nullable = false)
  private User createdBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Assignment() {}

  public Assignment(String title, String description, Instant startAt, Instant endAt, User createdBy) {
    this.title = title;
    this.description = description;
    this.startAt = startAt;
    this.endAt = endAt;
    this.createdBy = createdBy;
    this.createdAt = Instant.now();
  }

  public void update(String title, String description, Instant startAt, Instant endAt) {
    this.title = title;
    this.description = description;
    this.startAt = startAt;
    this.endAt = endAt;
  }

  public AssignmentStatus status() {
    Instant now = Instant.now();
    if (now.isBefore(startAt)) {
      return AssignmentStatus.UPCOMING;
    }
    if (now.isAfter(endAt)) {
      return AssignmentStatus.EXPIRED;
    }
    return AssignmentStatus.ACTIVE;
  }

  public Long getId() {
    return id;
  }

  public String getTitle() {
    return title;
  }

  public String getDescription() {
    return description;
  }

  public Instant getStartAt() {
    return startAt;
  }

  public Instant getEndAt() {
    return endAt;
  }

  public User getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
